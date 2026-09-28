package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.model.MarriageRecord;
import cn.mcxyd.xiyuanmarry.model.RewardTicket;
import cn.mcxyd.xiyuanmarry.repository.DatabaseManager;
import cn.mcxyd.xiyuanmarry.repository.DatabaseSettings;
import cn.mcxyd.xiyuanmarry.scheduler.TaskHandle;
import cn.mcxyd.xiyuanmarry.scheduler.UnifiedScheduler;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 实际 SQLite 事务，显式控制 Async 排队顺序；不启动真实 Minecraft 核心。 */
class AnniversaryRewardFlowTest {
    @TempDir Path root;
    final UnifiedScheduler scheduler = mock(UnifiedScheduler.class);
    final Queue<Runnable> tasks = new ArrayDeque<>();
    DatabaseManager database;
    DatabaseSettings settings;
    ConfigurationManager config;
    MarriageService marriages;
    RewardService rewards;
    IoDispatcher io;
    Runnable scan;

    @BeforeEach void setup() {
        var plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        config = new ConfigurationManager(plugin);
        var files = new HashMap<String, YamlConfiguration>();
        for (String name : List.of("config.yml", "rewards.yml", "messages.yml"))
            files.put(name, YamlConfiguration.loadConfiguration(Path.of("src/main/resources", name).toFile()));
        files.get("rewards.yml").set("weekly-top.enabled", false);
        settings = DatabaseSettings.sqlite(root.resolve("anniversary.db"));
        config.publish(new ConfigurationManager.Snapshot(UUID.randomUUID(), files, Map.of(), settings, null));
        database = new DatabaseManager(settings);
        when(scheduler.runAsync(any())).thenAnswer(call -> { tasks.add(call.getArgument(0)); return mock(TaskHandle.class); });
        when(scheduler.runGlobal(any())).thenReturn(mock(TaskHandle.class));
        when(scheduler.runRepeatingAsync(any(), anyLong(), anyLong(), any())).thenAnswer(call -> {
            if (call.<Long>getArgument(2) == 30L && call.getArgument(3) == TimeUnit.SECONDS) scan = call.getArgument(0);
            return mock(TaskHandle.class);
        });
        io = new IoDispatcher(scheduler);
        marriages = new MarriageService(plugin, config, mock(MessageService.class), database, scheduler, io, mock(PlayerDirectory.class));
        rewards = new RewardService(marriages, config, scheduler, database, io, plugin.getLogger());
        rewards.start();
        assertNotNull(scan);
    }
    @AfterEach void close() {
        if (rewards != null) rewards.close();
        if (marriages != null) marriages.shutdown();
        if (io != null) io.close();
        if (database != null) database.close();
        tasks.clear();
    }
    private void drain() {
        for (int n = 0; n < 100 && !tasks.isEmpty(); n++) tasks.remove().run();
        assertTrue(tasks.isEmpty());
    }
    private void refresh() { marriages.submit(null, r -> null, ignored -> {}); drain(); }
    private MarriageRecord seed() {
        var one = UUID.randomUUID();
        database.use(r -> { assertTrue(r.createMarriage(one, UUID.randomUUID(), "NORMAL", System.currentTimeMillis() - Duration.ofDays(8).toMillis())); return null; });
        refresh();
        return marriages.view().byPlayer().get(one);
    }
    private List<RewardTicket> tickets() {
        return database.use(r -> r.entries("reward-inbox").values().stream()
                .map(raw -> marriages.json().fromJson(raw, RewardTicket.class)).toList());
    }
    private Map<String, String> issued() { return database.use(r -> r.entries("reward-issued")); }
    private void assertNoRewards() { assertTrue(tickets().isEmpty()); assertTrue(issued().isEmpty()); }
    private void sql(String sql) throws Exception {
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + settings.file()); var statement = c.createStatement()) { statement.execute(sql); }
    }

    @Test void deletedRelationshipCannotReceiveQueuedAnniversaryRewards() {
        var marriage = seed(); scan.run();
        database.use(r -> r.deleteMarriage(marriage.playerOne()));
        drain(); assertNoRewards();
    }
    @Test void divorcePendingPausesQueuedRewardsAndWithdrawalCanRetry() {
        var marriage = seed(); scan.run();
        database.use(r -> r.requestDivorce(marriage.playerOne(), System.currentTimeMillis() + 100_000));
        drain(); assertNoRewards();
        database.use(r -> r.withdrawDivorce(marriage.playerOne())); refresh();
        scan.run(); drain(); assertEquals(2, tickets().size());
    }
    @Test void remarriageOfSamePlayersDoesNotInheritOldAnniversary() {
        var old = seed(); scan.run();
        database.use(r -> { r.deleteMarriage(old.playerOne()); assertTrue(r.createMarriage(old.playerOne(), old.playerTwo(), "NORMAL", System.currentTimeMillis())); return null; });
        drain(); assertNoRewards();
    }
    @Test void transactionRechecksActualMarriageDate() throws Exception {
        var marriage = seed(); scan.run();
        sql("UPDATE marriages SET married_at=" + System.currentTimeMillis());
        drain(); assertNoRewards();
    }
    @Test void secondPartnerWriteFailureRollsBackBothTicketsAndMarkers() throws Exception {
        var marriage = seed(); scan.run();
        // 只拒绝第二位收件人的入箱写入，验证真实事务的部分失败边界。
        sql("CREATE TRIGGER reject_second BEFORE INSERT ON xym_metadata WHEN NEW.bucket='reward-inbox' AND instr(NEW.payload,'"
                + marriage.playerTwo() + "')>0 BEGIN SELECT RAISE(ABORT,'test second recipient failure'); END");
        drain(); assertNoRewards();
        sql("DROP TRIGGER reject_second");
        scan.run(); drain(); assertEquals(2, tickets().size()); assertEquals(2, issued().size());
    }
    @Test void secondPartnerMarkerFailureRollsBackBothTicketsAndMarkers() throws Exception {
        var marriage = seed(); scan.run();
        sql("CREATE TRIGGER reject_marker BEFORE INSERT ON xym_metadata WHEN NEW.bucket='reward-issued' AND NEW.entry_key='"
                + marriage.id() + ":7:" + marriage.playerTwo() + "' BEGIN SELECT RAISE(ABORT,'test marker failure'); END");
        drain(); assertNoRewards();
        sql("DROP TRIGGER reject_marker"); scan.run(); drain();
        assertEquals(2, tickets().size()); assertEquals(2, issued().size());
    }
    @Test void newFailurePreservesPreviouslyIssuedPartnerMarker() throws Exception {
        var marriage = seed();
        String marker = marriage.id() + ":7:" + marriage.playerOne();
        database.use(r -> { r.put("reward-issued", marker, "legacy-consumed-ticket"); return null; });
        sql("CREATE TRIGGER reject_remaining BEFORE INSERT ON xym_metadata WHEN NEW.bucket='reward-inbox' BEGIN SELECT RAISE(ABORT,'test remaining recipient'); END");
        scan.run(); drain();
        assertTrue(tickets().isEmpty()); assertEquals(Map.of(marker, "legacy-consumed-ticket"), issued());
        sql("DROP TRIGGER reject_remaining"); scan.run(); drain();
        assertEquals(1, tickets().size()); assertEquals(marriage.playerTwo(), tickets().getFirst().recipient());
    }
    @Test void laterDivorceDoesNotRemoveAlreadyCommittedTickets() {
        var marriage = seed(); scan.run(); drain();
        var before = new HashSet<>(tickets()); var markers = issued();
        database.use(r -> r.deleteMarriage(marriage.playerOne())); refresh(); scan.run(); drain();
        assertEquals(before, new HashSet<>(tickets())); assertEquals(markers, issued());
    }
    @Test void repeatedAndOverlappingScansIssueExactlyOneTicketPerPartner() {
        var marriage = seed(); scan.run(); scan.run(); drain(); scan.run(); drain();
        assertEquals(2, tickets().size()); assertEquals(2, issued().size());
        assertEquals(Set.of(marriage.playerOne(), marriage.playerTwo()), new HashSet<>(tickets().stream().map(RewardTicket::recipient).toList()));
        assertTrue(tickets().stream().allMatch(t -> t.relationship().equals(marriage.id()) && t.anniversaryDays() == 7 && t.money() == 500 && t.experience() == 100));
    }
    @Test void legacySinglePartnerMarkerIsPreservedWithoutRecreatingClaimedTicket() {
        var marriage = seed();
        String marker = marriage.id() + ":7:" + marriage.playerOne();
        database.use(r -> { r.put("reward-issued", marker, "legacy-consumed-ticket"); return null; });
        scan.run(); drain(); scan.run(); drain();
        assertEquals(1, tickets().size()); assertEquals(marriage.playerTwo(), tickets().getFirst().recipient());
        assertEquals("legacy-consumed-ticket", issued().get(marker)); assertEquals(2, issued().size());
    }
    @Test void claimedTicketsAreNotRecreatedByLaterScans() {
        seed(); scan.run(); drain();
        database.use(r -> { for (String id : r.entries("reward-inbox").keySet()) r.remove("reward-inbox", id); return null; });
        scan.run(); drain(); assertTrue(tickets().isEmpty()); assertEquals(2, issued().size());
    }
    @Test void databaseSwitchRejectsQueuedOldCatalogAndPreservesBothDatabases() {
        seed(); scan.run();
        database.switchTo(DatabaseSettings.sqlite(root.resolve("new.db"))); drain(); assertNoRewards();
        // 切库尚未发布新目录时，旧扫描也不能把旧婚姻写到新库。
        scan.run(); drain(); assertNoRewards();
        database.switchTo(settings); assertNoRewards();
        rewards.reload(); refresh(); scan.run(); drain(); assertEquals(2, tickets().size());
    }
    @Test void reloadDropsQueuedCatalogAndNextScanUsesNewAmounts() {
        seed(); scan.run();
        var previous = config.snapshot();
        var files = new HashMap<>(previous.files());
        var rewardFile = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/rewards.yml").toFile());
        rewardFile.set("weekly-top.enabled", false); rewardFile.set("anniversaries.7.money", 123);
        files.put("rewards.yml", rewardFile);
        config.publish(new ConfigurationManager.Snapshot(UUID.randomUUID(), files, Map.of(), settings, null));
        rewards.reload(); drain(); assertNoRewards();
        scan.run(); drain(); assertEquals(2, tickets().size());
        assertTrue(tickets().stream().allMatch(t -> t.money() == 123));
    }
    @Test void closePreventsQueuedAndNewScansFromIssuing() {
        seed(); scan.run(); rewards.close(); drain(); assertNoRewards(); scan.run(); drain(); assertNoRewards();
    }
}
