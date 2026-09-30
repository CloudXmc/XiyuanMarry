package cn.mcxyd.xiyuanmarry;

import cn.mcxyd.xiyuanmarry.command.*;
import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.gui.*;
import cn.mcxyd.xiyuanmarry.listener.CoupleTaskListener;
import cn.mcxyd.xiyuanmarry.listener.PartnerLifecycleListener;
import cn.mcxyd.xiyuanmarry.listener.PlayerLifecycleListener;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.placeholder.PlaceholderApiHook;
import cn.mcxyd.xiyuanmarry.repository.DatabaseManager;
import cn.mcxyd.xiyuanmarry.scheduler.UnifiedScheduler;
import cn.mcxyd.xiyuanmarry.service.*;
import org.bukkit.command.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;
import java.util.logging.Level;

/** 只负责插件生命周期、依赖装配和关闭顺序。 */
public final class XiyuanMarryPlugin extends JavaPlugin {
    private final Object lifecycle = new Object();
    private volatile boolean stopping, startupFailed, activated;
    private boolean businessClosed;
    private CommandExecutor playerExecutor, adminExecutor;
    private TabCompleter playerCompleter, adminCompleter;
    private ConfigurationManager config;
    private UnifiedScheduler scheduler;
    private DatabaseManager database;
    private IoDispatcher io;
    private PlayerDirectory directory;
    private MessageService messages;
    private MarriageService marriages;
    private BondAttributeService attributes;
    private WeddingService weddings;
    private DailyTaskService dailyTasks;
    private SharedOnlineService sharedOnline;
    private CoupleTaskListener coupleTasks;
    private GiftService gifts;
    private RewardService rewards;
    private ClaimInboxService inbox;
    private GuiFactory gui;
    private PartnerTeleportService teleports;
    private RingService ring;
    private PlaceholderApiHook placeholders;

    @Override
    public void onEnable() {
        FoliaSupport.detectOnce();
        saveDefaultConfig();
        for (String resource : ConfigurationManager.GUI_NAMES.stream().map(x -> "gui/" + x + ".yml").toList()) saveResourceIfMissing(resource);
        for (String resource : List.of("messages.yml", "tasks.yml", "rank.yml", "rewards.yml", "weddings.yml")) saveResourceIfMissing(resource);
        config = new ConfigurationManager(this);
        config.load();
        scheduler = new UnifiedScheduler(this);
        messages = new MessageService(config);
        database = new DatabaseManager(config.snapshot().database(), false);
        io = new IoDispatcher(scheduler, getLogger());
        directory = new PlayerDirectory(config, scheduler);
        // 装配不做 IO；先保存稳定服务引用，关闭路径始终能终止它。
        marriages = new MarriageService(this, config, messages, database, scheduler, io, directory, false);
        registerLoadingCommands();
        if (!io.submit(this::initializeDatabase)) {
            startupFailed = true;
            marriages.shutdown();
            // 入队被拒绝时尚未建池，关闭只标记终态，不执行数据库 IO。
            database.close();
            getLogger().severe("结婚系统数据库初始化任务未能排队，业务将保持关闭。");
        }
        getLogger().info("结婚系统 XiyuanMarry 正在加载；Folia=" + FoliaSupport.isFolia());
    }

    private void initializeDatabase() {
        if (stopping) return;
        try {
            database.initialize();
            if (stopping) return;
            marriages.initializeAfterDatabase();
            synchronized (lifecycle) {
                if (stopping) return;
                if (!marriages.ready()) throw new IllegalStateException("初始婚姻快照尚未就绪");
                scheduler.runGlobal(this::activateServices);
            }
        } catch (Throwable error) {
            if (stopping) return;
            startupFailed = true;
            marriages.shutdown();
            safe(database::close);
            getLogger().log(Level.SEVERE, "结婚系统数据库初始化失败，业务将保持关闭", error);
        }
    }

    /** 事件注册、GUI 和命令必须在全局上下文完成；数据库快照已在异步阶段加载。 */
    private void activateServices() {
        synchronized (lifecycle) {
            if (stopping || startupFailed || activated || marriages == null || !marriages.ready()) return;
            try {
                marriages.initialize();
                attributes = new BondAttributeService(this, config, marriages, scheduler);
                attributes.start();
                weddings = new WeddingService(marriages, config, scheduler);
                dailyTasks = new DailyTaskService(marriages, config, scheduler);
                sharedOnline = new SharedOnlineService(marriages, config, scheduler);
                coupleTasks = new CoupleTaskListener(marriages, dailyTasks, scheduler);
                gifts = new GiftService(marriages, scheduler, database);
                rewards = new RewardService(marriages, config, scheduler, database, io, getLogger());
                rewards.start();
                inbox = new ClaimInboxService(marriages, rewards, gifts, scheduler, database, config, messages);
                teleports = new PartnerTeleportService(marriages, config, database, io, scheduler, getLogger());
                ring = new RingService(this, marriages, config, directory, scheduler, messages);
                ring.start();
                gui = new GuiFactory(config, marriages, weddings, messages, scheduler, dailyTasks, inbox);
                marriages.onReload(() -> {
                    dailyTasks.reload(); sharedOnline.reload(); coupleTasks.reload(); gui.reload(); attributes.reload();
                    weddings.clear(); teleports.reload(); rewards.reload(); ring.reload();
                    if (placeholders != null) placeholders.reload();
                });
                if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                    placeholders = new PlaceholderApiHook(this, marriages);
                    placeholders.register();
                }
                MarryCommand player = new MarryCommand(messages, directory, marriages, weddings, gui);
                MarriageSubcommands.register(player, marriages, weddings, directory, messages, gui, gifts, ring, rewards);
                player.add(new ClaimSubcommand(inbox, rewards, gifts, directory, messages));
                player.add(new TeleportSubcommand(teleports, directory, messages));
                MarryAdminCommand admin = new MarryAdminCommand(messages);
                AdminSubcommands.register(admin, marriages, directory, messages, ring);
                gui.commands((p, a) -> player.onCommand(p, getCommand("marry"), "marry", a));
                playerExecutor = player;
                playerCompleter = new SubcommandCompleter(player.commands());
                adminExecutor = admin;
                adminCompleter = new SubcommandCompleter(admin.commands());
                getServer().getPluginManager().registerEvents(new PartnerLifecycleListener(teleports), this);
                getServer().getPluginManager().registerEvents(new GuiListener(gui, scheduler), this);
                getServer().getPluginManager().registerEvents(new PlayerLifecycleListener(directory, weddings, marriages, attributes), this);
                getServer().getPluginManager().registerEvents(sharedOnline, this);
                getServer().getPluginManager().registerEvents(coupleTasks, this);
                directory.bootstrap();
                coupleTasks.bootstrap();
                activated = true;
                getLogger().info("结婚系统 XiyuanMarry " + getDescription().getVersion() + " 已启用；Folia=" + FoliaSupport.isFolia());
            } catch (Throwable error) {
                startupFailed = true;
                activated = false;
                stopBusiness();
                // 回滚模块在当前全局上下文执行，连接池销毁仍交给异步队列。
                if (!io.submit(() -> safe(database::close)))
                    getLogger().severe("启动失败后的数据库清理未能排队，将在插件关闭时再次清理。");
                getLogger().log(Level.SEVERE, "结婚系统业务激活失败，已关闭业务入口", error);
            }
        }
    }

    private void registerLoadingCommands() {
        installCommandGate("marry", () -> playerExecutor, () -> playerCompleter);
        installCommandGate("marryadmin", () -> adminExecutor, () -> adminCompleter);
    }

    private boolean commandsReady() { return activated && !stopping && !startupFailed; }

    private void installCommandGate(String name, java.util.function.Supplier<CommandExecutor> executor,
                                    java.util.function.Supplier<TabCompleter> completer) {
        var command = java.util.Objects.requireNonNull(getCommand(name), "未声明主指令 " + name);
        // 入口始终保留门禁，部分激活失败和关闭时也不会暴露半初始化服务。
        command.setExecutor((sender, cmd, label, args) -> {
            if (!commandsReady()) {
                messages.send(sender, startupFailed ? "startup-failed" : "database-not-ready");
                return true;
            }
            return executor.get().onCommand(sender, cmd, label, args);
        });
        command.setTabCompleter((sender, cmd, label, args) -> commandsReady()
                ? completer.get().onTabComplete(sender, cmd, label, args) : List.of());
    }

    @Override
    public void onDisable() {
        synchronized (lifecycle) { stopping = true; activated = false; stopBusiness(); }
        safe(() -> { if (io != null) io.close(); });
        safe(() -> { if (database != null) database.close(); });
        safe(() -> { if (scheduler != null) scheduler.close(); });
    }

    /** 只由全局激活/停服路径调用，清理部分装配的模块且只执行一次。 */
    private void stopBusiness() {
        if (businessClosed) return;
        businessClosed = true;
        safe(() -> { if (placeholders != null) placeholders.close(); });
        safe(() -> { if (rewards != null) rewards.close(); });
        safe(() -> { if (inbox != null) inbox.close(); });
        safe(() -> { if (gifts != null) gifts.close(); });
        safe(() -> { if (ring != null) ring.close(); });
        safe(() -> { if (attributes != null) attributes.close(); });
        safe(() -> { if (teleports != null) teleports.close(); });
        safe(() -> { if (sharedOnline != null) sharedOnline.close(); });
        safe(() -> { if (coupleTasks != null) coupleTasks.close(); });
        safe(() -> { if (gui != null) gui.close(); });
        safe(() -> { if (weddings != null) weddings.close(); });
        safe(() -> { if (dailyTasks != null) dailyTasks.close(); });
        safe(() -> { if (marriages != null) marriages.shutdown(); });
        safe(() -> { if (directory != null) directory.close(); });
        org.bukkit.event.HandlerList.unregisterAll(this);
    }

    private void safe(Runnable action) { try { action.run(); } catch (Throwable error) { getLogger().log(Level.SEVERE, "关闭阶段清理失败", error); } }
    public ConfigurationManager configurationManager() { return config; }
    public MarriageService marriageService() { return marriages; }
    private void saveResourceIfMissing(String resource) { if (!getDataFolder().toPath().resolve(resource).toFile().exists()) saveResource(resource, false); }
}
