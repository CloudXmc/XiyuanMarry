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
    private final MarriageService marriages;
    private final ConfigurationManager config;
    private final PlayerDirectory directory;
    private final UnifiedScheduler scheduler;
    private final NamespacedKey ringKey;
    private final MessageService messages;
    private final ConcurrentHashMap<UUID, Boolean> enabled = new ConcurrentHashMap<>();
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

    public void start() {
        task = scheduler.runRepeatingAsync(this::tick, 1, 2, TimeUnit.SECONDS);
    }

    public boolean toggle(PlayerSnapshot actor) {
        boolean next = !enabled.getOrDefault(actor.id(), true);
        enabled.put(actor.id(), next);
        marriages.notifyLive(actor.liveId(), "ring-toggle", "state", messages.raw(next ? "on" : "off"));
        return next;
    }

    public void reload() {
        enabled.keySet().retainAll(marriages.view().profiles().keySet());
    }

    public void give(CommandSender sender, UUID target, String rawType) {
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
            if (player.getInventory().firstEmpty() < 0) {
                messages.send(player, "inventory-full");
                notifySender(sender, "inventory-full");
                return;
            }
            player.getInventory().setItem(player.getInventory().firstEmpty(), create(type));
            marriages.notifyLive(recipient.liveId(), "ring-given", "type", messages.raw("ring-" + type));
            notifySender(sender, "ring-give-success", "player", recipient.name(), "type", messages.raw("ring-" + type));
        }, () -> messages.send(sender, "offline"));
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
        for (PlayerSnapshot snapshot : directory.all()) scheduler.player(snapshot.liveId(), player -> apply(player, snapshot));
    }

    private void apply(Player player, PlayerSnapshot snapshot) {
        MarriageRecord marriage = marriages.view().byPlayer().get(snapshot.id());
        PlayerSnapshot partner = marriage == null ? null : directory.identity(marriage.partnerOf(snapshot.id()));
        ItemStack offhand = player.getInventory().getItemInOffHand();
        String type = ringType(offhand);
        boolean sameWorld = partner != null && player.getWorld().getUID().equals(partner.point().world());
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

    private void notifySender(CommandSender sender, String key, Object... values) {
        if (sender instanceof Player player) scheduler.player(player.getUniqueId(), p -> messages.send(p, key, values));
        else scheduler.runGlobal(() -> messages.send(sender, key, values));
    }

    @Override public void close() {
        closed = true;
        if (task != null) task.cancel();
        enabled.clear();
    }
}
