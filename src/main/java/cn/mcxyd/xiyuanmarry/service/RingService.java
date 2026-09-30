package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.model.MarriageRecord;
import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;
import cn.mcxyd.xiyuanmarry.scheduler.TaskHandle;
import cn.mcxyd.xiyuanmarry.scheduler.UnifiedScheduler;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** 管理戒指物品、开关和共生效果；实时物品与药水只在玩家实体上下文访问。 */
public final class RingService implements AutoCloseable {
    private static final String ENGAGEMENT = "engagement";
    private static final String MARRIAGE = "marriage";
    /** 开关偏好按持久身份落库；键为身份 ID，值为 on/off。 */
    static final String PREF_BUCKET = "ring-prefs";
    private final MarriageService marriages;
    private final ConfigurationManager config;
    private final PlayerDirectory directory;
    private final UnifiedScheduler scheduler;
    private final NamespacedKey ringKey;
    private final MessageService messages;
    private final ConcurrentHashMap<UUID, Boolean> enabled = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> pending = new ConcurrentHashMap<>();
    private volatile TaskHandle task;
    private volatile boolean closed;

    public RingService(org.bukkit.plugin.java.JavaPlugin plugin, MarriageService marriages,
                       ConfigurationManager config, PlayerDirectory directory,
                       UnifiedScheduler scheduler, MessageService messages) {
        this.marriages = marriages;
        this.config = config;
        this.directory = directory;
        this.scheduler = scheduler;
        this.messages = messages;
        this.ringKey = new NamespacedKey(plugin, "ring-type");
    }

    public synchronized void start() {
        if(closed||task!=null)return;
        task = scheduler.runRepeatingAsync(this::tick, 1, 2, TimeUnit.SECONDS);
        loadPreferences();
    }

    /** 开关偏好按持久身份入库，首次激活时回填内存缓存；损坏键忽略并保持默认开启。 */
    private void loadPreferences() {
        if(closed)return;
        marriages.submit(null, r -> r.entries(PREF_BUCKET), prefs -> {
            if(closed)return;
            for(var entry : prefs.entrySet()) try {
                // putIfAbsent：加载是异步的，玩家在加载完成前的操作必须胜过库里的旧值。
                enabled.putIfAbsent(UUID.fromString(entry.getKey()), !"off".equals(entry.getValue()));
            } catch(RuntimeException invalid) {
                config.warn("戒指开关偏好键无效，已忽略：" + entry.getKey());
            }
        }, error -> {}, () -> {});
    }

    public boolean toggle(PlayerSnapshot actor) {
        if(closed)return false;
        boolean next = !enabled.getOrDefault(actor.id(), true);
        enabled.put(actor.id(), next);
        persistPreference(actor.id(), next);
        marriages.notifyLive(actor.liveId(), "ring-toggle", "state", messages.raw(next ? "on" : "off"));
        return next;
    }

    private void persistPreference(UUID identity, boolean on) {
        marriages.submit(null, r -> {
            // 重新确认身份仍存在，避免删除资料后的迟到开关重新写回偏好。
            if(!marriages.view().profiles().containsKey(identity))return null;
            r.put(PREF_BUCKET, identity.toString(), on ? "on" : "off");
            return null;
        }, x -> {}, error -> config.warn("戒指开关偏好写入失败，本次仅内存生效：" + error));
    }

    public void reload() {
        // 切库或重载后重新读取该库的偏好；内存缓存只保留仍有资料的玩家。
        enabled.keySet().retainAll(marriages.view().profiles().keySet());
        loadPreferences();
    }

    public void give(CommandSender sender, UUID target, String rawType) {
        if (closed) return;
        // 在指令发起者上下文读取身份，目标玩家回调不再持有或读取发起者 Player。
        UUID senderId = sender instanceof Player player ? player.getUniqueId() : null;
        String type = rawType.toLowerCase(Locale.ROOT);
        if (!type.equals(ENGAGEMENT) && !type.equals(MARRIAGE)) {
            messages.send(sender, "invalid-argument");
            return;
        }
        PlayerSnapshot recipient = directory.identity(target);
        if (recipient == null) {
            messages.send(sender, "offline");
            return;
        }
        scheduler.player(recipient.liveId(), player -> {
            if (closed) return;
            if (player.getInventory().firstEmpty() < 0) {
                messages.send(player, "inventory-full");
                notifySender(senderId, "inventory-full");
                return;
            }
            player.getInventory().setItem(player.getInventory().firstEmpty(), create(type));
            marriages.notifyLive(recipient.liveId(), "ring-given", "type", messages.raw("ring-" + type));
            notifySender(senderId, "ring-give-success", "player", recipient.name(), "type", messages.raw("ring-" + type));
        }, () -> notifySender(senderId, "offline"));
    }

    private ItemStack create(String type) {
        String path = "privileges.ring-materials." + type;
        Material material = Material.matchMaterial(config.config().getString(path, type.equals(MARRIAGE) ? "DIAMOND" : "GOLD_NUGGET"));
        if (material == null || !material.isItem()) material = type.equals(MARRIAGE) ? Material.DIAMOND : Material.GOLD_NUGGET;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.parse(messages.raw("ring-" + type)));
        meta.lore(List.of(messages.parse(messages.raw("ring-lore"))));
        meta.getPersistentDataContainer().set(ringKey, PersistentDataType.STRING, type);
        item.setItemMeta(meta);
        return item;
    }

    private void tick() {
        if (closed) return;
        var online=directory.all();
        // 按已知资料而非在线集合清理：退出后偏好必须保留，否则重新登录会静默恢复默认开启。
        var known=marriages.view();
        if(known!=null)enabled.keySet().retainAll(known.profiles().keySet());
        for (PlayerSnapshot snapshot : online) {
            UUID live=snapshot.liveId(),token=UUID.randomUUID();
            synchronized(this){
                if(closed)return;
                if(pending.putIfAbsent(live,token)!=null)continue;
            }
            // 每名玩家最多一项待执行回调；退休/失败也释放标记，防止卡顿区域累积任务。
            try{scheduler.player(live,player->{
                try{
                    if(closed||player.isDead())return;
                    var current=directory.live(live);
                    if(current!=null)apply(player,current);
                }finally{pending.remove(live,token);}
            },()->pending.remove(live,token));}
            catch(RuntimeException failure){pending.remove(live,token);throw failure;}
        }
    }

    private void apply(Player player, PlayerSnapshot snapshot) {
        if (closed) return;
        MarriageRecord marriage = marriages.view().byPlayer().get(snapshot.id());
        PlayerSnapshot partner = marriage == null ? null : directory.identity(marriage.partnerOf(snapshot.id()));
        // 目录快照可能在玩家切换世界或退出时暂时没有坐标；缺少不可变目标坐标时直接跳过本轮。
        if (marriage == null || partner == null || partner.point() == null || player.getWorld() == null) return;
        ItemStack offhand = player.getInventory().getItemInOffHand();
        String type = ringType(offhand);
        boolean sameWorld = player.getWorld().getUID().equals(partner.point().world());
        double distanceSquared = Double.MAX_VALUE;
        if (sameWorld) {
            var location = player.getLocation();
            double dx = location.getX() - partner.point().x();
            double dy = location.getY() - partner.point().y();
            double dz = location.getZ() - partner.point().z();
            distanceSquared = dx * dx + dy * dy + dz * dz;
        }
        boolean active = RingPolicy.active(enabled.getOrDefault(snapshot.id(), true), MARRIAGE.equals(type), marriage,
                sameWorld, distanceSquared, config.config().getDouble("privileges.buff-distance", 20));
        if (!active) return;
        int duration = Math.max(1, config.config().getInt("privileges.ring-buff-duration-ticks", 120));
        int speed = Math.max(0, config.config().getInt("privileges.ring-effects.speed-amplifier", 0));
        int resistance = Math.max(0, config.config().getInt("privileges.ring-effects.resistance-amplifier", 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, speed, true, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, resistance, true, false, true));
    }

    private String ringType(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return "";
        String type = item.getItemMeta().getPersistentDataContainer().get(ringKey, PersistentDataType.STRING);
        return type == null ? "" : type;
    }

    private void notifySender(UUID senderId, String key, Object... values) {
        if (closed) return;
        if (senderId != null) scheduler.player(senderId, p -> {if (!closed) messages.send(p, key, values);});
        else scheduler.runGlobal(() -> {if (!closed) messages.send(org.bukkit.Bukkit.getConsoleSender(), key, values);});
    }

    @Override public synchronized void close() {
        closed = true;
        if (task != null) task.cancel();
        enabled.clear();
        pending.clear();
    }
}
