package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.model.MarriageRecord;
import cn.mcxyd.xiyuanmarry.model.MarriageState;
import cn.mcxyd.xiyuanmarry.scheduler.TaskHandle;
import cn.mcxyd.xiyuanmarry.scheduler.UnifiedScheduler;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** 按情侣羁绊等级维护临时属性；只在玩家实体所有者上下文读写 AttributeInstance。 */
public final class BondAttributeService implements AutoCloseable {
    public record Bonus(double maxHealth, double attackDamage, double movementSpeed) {}

    private final ConfigurationManager config;
    private final MarriageService marriages;
    private final UnifiedScheduler scheduler;
    private final Map<UUID, Integer> applied = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private final NamespacedKey maxHealthKey;
    private final NamespacedKey attackDamageKey;
    private final NamespacedKey movementSpeedKey;
    private volatile boolean closed;
    private TaskHandle timer;

    public BondAttributeService(JavaPlugin plugin, ConfigurationManager config, MarriageService marriages, UnifiedScheduler scheduler) {
        this.config = config;
        this.marriages = marriages;
        this.scheduler = scheduler;
        maxHealthKey = new NamespacedKey(plugin, "bond-max-health");
        attackDamageKey = new NamespacedKey(plugin, "bond-attack-damage");
        movementSpeedKey = new NamespacedKey(plugin, "bond-movement-speed");
    }

    public synchronized void start() {
        if (closed || timer!=null) return;
        timer = scheduler.runRepeatingAsync(this::sweep, 1, 1, TimeUnit.SECONDS);
    }

    public void reload() {
        if (closed) return;
        applied.clear();
        // 已排队回调会读取最新配置，不清空 pending，避免 reload 重复排队。
        for (var snapshot : marriages.directory().all()) schedule(snapshot.liveId());
    }

    public void join(Player player) { if (!closed) reconcile(player); }

    public void refresh(Player player) {
        if(closed)return;
        // 死亡会使核心重建属性；先使缓存失效，复活后的轮询才会重新应用相同等级。
        applied.remove(player.getUniqueId());
        if(!player.isDead())join(player);
    }

    public void quit(Player player) {
        if (closed) return;
        remove(player);
        applied.remove(player.getUniqueId());
        pending.remove(player.getUniqueId());
    }

    private void sweep() {
        if (closed) return;
        var seen = ConcurrentHashMap.<UUID>newKeySet();
        for (var snapshot : marriages.directory().all()) {
            seen.add(snapshot.liveId());
            var record = marriages.view().byPlayer().get(snapshot.id());
            int desired = active(record) ? marriages.level(record.bond()) : 0;
            if (!Integer.valueOf(desired).equals(applied.get(snapshot.liveId()))) schedule(snapshot.liveId());
        }
        applied.keySet().removeIf(id -> !seen.contains(id));
    }

    private void schedule(UUID liveId) {
        if (closed || !pending.add(liveId)) return;
        scheduler.player(liveId, player -> {
            pending.remove(liveId);
            reconcile(player);
        }, () -> pending.remove(liveId));
    }

    private void reconcile(Player player) {
        if (closed || !player.isOnline() || player.isDead()) return;
        var snapshot = marriages.directory().live(player.getUniqueId());
        var record = snapshot == null ? null : marriages.view().byPlayer().get(snapshot.id());
        int level = active(record) ? marriages.level(record.bond()) : 0;
        if (level == 0) {
            remove(player, true);
            applied.put(player.getUniqueId(), 0);
            return;
        }
        apply(player, bonus(level));
        applied.put(player.getUniqueId(), level);
    }

    private boolean active(MarriageRecord record) { return record != null && record.state() == MarriageState.MARRIED; }

    private void apply(Player player, Bonus bonus) {
        remove(player, false);
        add(player, Attribute.MAX_HEALTH, maxHealthKey, bonus.maxHealth());
        add(player, Attribute.ATTACK_DAMAGE, attackDamageKey, bonus.attackDamage());
        add(player, Attribute.MOVEMENT_SPEED, movementSpeedKey, bonus.movementSpeed());
        clampHealth(player);
    }

    private void add(Player player, Attribute attribute, NamespacedKey key, double amount) {
        if (amount == 0) return;
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) instance.addTransientModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER));
    }

    private void remove(Player player) {
        remove(player, true);
    }

    private void remove(Player player, boolean clampHealth) {
        remove(player, Attribute.MAX_HEALTH, maxHealthKey);
        remove(player, Attribute.ATTACK_DAMAGE, attackDamageKey);
        remove(player, Attribute.MOVEMENT_SPEED, movementSpeedKey);
        if (clampHealth) clampHealth(player);
    }

    private void clampHealth(Player player) {
        AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
        if (health != null && player.getHealth() > health.getValue()) player.setHealth(health.getValue());
    }

    private void remove(Player player, Attribute attribute, NamespacedKey key) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        var modifier = instance.getModifier(key);
        if (modifier != null) instance.removeModifier(modifier);
    }

    public Bonus bonus(int level) {
        return calculate(level, config.config().getBoolean("bond.attributes.enabled", true),
                config.config().getDouble("bond.attributes.per-level.max-health", 1.0),
                config.config().getDouble("bond.attributes.per-level.attack-damage", 0.25),
                config.config().getDouble("bond.attributes.per-level.movement-speed", 0.005));
    }

    public static Bonus calculate(int level, boolean enabled, double maxHealthPerLevel, double attackPerLevel, double speedPerLevel) {
        if (!Double.isFinite(maxHealthPerLevel) || maxHealthPerLevel < 0
                || !Double.isFinite(attackPerLevel) || attackPerLevel < 0
                || !Double.isFinite(speedPerLevel) || speedPerLevel < 0) {
            throw new IllegalArgumentException("情侣属性增量必须是非负有限数字");
        }
        if (!enabled || level <= 1) return new Bonus(0, 0, 0);
        int steps = Math.min(level, 10) - 1;
        return new Bonus(maxHealthPerLevel * steps, attackPerLevel * steps, speedPerLevel * steps);
    }

    @Override public synchronized void close() {
        if(closed)return;
        closed=true;
        if (timer != null) timer.cancel();
        // 关闭后只提交属性清理；真实停服可能拒绝或取消实体调度，不能承诺必定执行。
        for (var snapshot : marriages.directory().all()) {
            try {
                scheduler.player(snapshot.liveId(), this::remove, () -> {});
            } catch (RuntimeException ignored) {
                // Paper 在插件已进入 disabled 状态时会拒绝注册实体任务；停服无需再写回实时属性。
            }
        }
        pending.clear();
        applied.clear();
    }
}
