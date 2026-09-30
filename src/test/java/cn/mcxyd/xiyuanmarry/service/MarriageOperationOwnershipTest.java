package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.model.*;
import cn.mcxyd.xiyuanmarry.repository.*;
import cn.mcxyd.xiyuanmarry.scheduler.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实串行队列/SQLite，只有测试工作线程受有超时的屏障控制，不直接插改数据库。 */
class MarriageOperationOwnershipTest {
    @TempDir Path root;
    final UnifiedScheduler scheduler=mock(UnifiedScheduler.class);
    final MessageService messages=mock(MessageService.class);
    final Queue<Runnable> workers=new ConcurrentLinkedQueue<>(), owners=new ConcurrentLinkedQueue<>();
    final Map<UUID,Player> online=new ConcurrentHashMap<>();
    ConfigurationManager config; DatabaseManager database; IoDispatcher io;
    PlayerDirectory directory; MarriageService marriages; Player alice,bob;

    @BeforeEach void setup(){
        var plugin=mock(JavaPlugin.class);when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        config=new ConfigurationManager(plugin);var files=new HashMap<String,YamlConfiguration>();
        for(String name:List.of("config.yml","messages.yml"))
            files.put(name,YamlConfiguration.loadConfiguration(Path.of("src/main/resources",name).toFile()));
        var settings=DatabaseSettings.sqlite(root.resolve("ownership.db"));
        config.publish(new ConfigurationManager.Snapshot(UUID.randomUUID(),files,Map.of(),settings,null));
        database=new DatabaseManager(settings);
        when(scheduler.runAsync(any())).thenAnswer(c->{workers.add(c.getArgument(0));return mock(TaskHandle.class);});
        when(scheduler.repeatEntity(any(),any(),anyLong())).thenReturn(mock(TaskHandle.class));
        doAnswer(c->{UUID id=c.getArgument(0);Consumer<Player> callback=c.getArgument(1);
            owners.add(()->{Player p=online.get(id);if(p!=null)callback.accept(p);});return null;
        }).when(scheduler).player(any(),any());
        directory=new PlayerDirectory(config,scheduler);io=new IoDispatcher(scheduler);
        marriages=new MarriageService(plugin,config,messages,database,scheduler,io,directory);
        alice=player("Alice");bob=player("Bob");
    }
    Player player(String name){
        var p=mock(Player.class);var world=mock(World.class);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());when(p.getName()).thenReturn(name);
        when(world.getUID()).thenReturn(UUID.randomUUID());when(p.getLocation()).thenReturn(new Location(world,0,70,0));
        when(p.getStatistic(Statistic.PLAY_ONE_MINUTE)).thenReturn(72_000);
        online.put(p.getUniqueId(),p);directory.join(p);return p;
    }
    void owners(){Runnable task;while((task=owners.poll())!=null)task.run();}
    static void await(CountDownLatch latch){
        try{assertTrue(latch.await(5,TimeUnit.SECONDS),"测试屏障未在期限内释放");}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
    }
    @AfterEach void close(){marriages.shutdown();directory.close();io.close();database.close();}

    void request(String action,PlayerSnapshot a,PlayerSnapshot b){
        switch(action){
            case "propose"->marriages.propose(a,b,"NORMAL");
            case "accept"->marriages.accept(b);
            case "deny"->marriages.deny(b);
            default->throw new AssertionError(action);
        }
    }

    @ParameterizedTest @CsvSource({"success,propose","rule-failure,propose","callback-failure,propose","success,accept","success,deny"})
    void finishingOldWorkCannotUnlockRequestQueuedAfterReconnect(String outcome,String action) throws Exception {
        var oldEntered=new CountDownLatch(1);var finishOld=new CountDownLatch(1);
        var gapEntered=new CountDownLatch(1);var finishGap=new CountDownLatch(1);
        var executor=Executors.newSingleThreadExecutor();Future<?> running=null;
        Player actor=action.equals("propose")?alice:bob;
        UUID live=actor.getUniqueId(), id=directory.live(live).id();
        try{
            marriages.submit(live,r->{
                oldEntered.countDown();await(finishOld);
                if(outcome.equals("rule-failure"))throw new RuleViolation("request-missing");return null;
            },ignored->{if(outcome.equals("callback-failure"))throw new IllegalStateException("expected test callback failure");});
            running=executor.submit(Objects.requireNonNull(workers.poll()));await(oldEntered);
            // 对应退出所有者线程：旧事务仍在运行，新登录随后提交普通求婚。
            directory.leave(live);marriages.leave(live,id);directory.join(actor);
            var a=directory.live(alice.getUniqueId());var b=directory.live(bob.getUniqueId());
            if(!action.equals("propose"))marriages.propose(a,b,"NORMAL");
            marriages.submit(null,r->{gapEntered.countDown();await(finishGap);return null;},ignored->{});
            request(action,a,b);
            finishOld.countDown();await(gapEntered);
            request(action,a,b);owners();
            // 此刻新求婚仍未执行，重复命令必须被门闩拒绝，而非进入 IO 队列。
            verify(messages).send(actor,"busy");
            finishGap.countDown();running.get(5,TimeUnit.SECONDS);owners();
            assertEquals(1L,database.<Long>use(r->MarriageService.number(r,"daily",marriages.today()+":"+a.id())));
            if(action.equals("propose"))assertNotNull(database.use(r->r.get("proposals",b.id().toString())));
            else assertNull(database.use(r->r.get("proposals",b.id().toString())));
            assertEquals(action.equals("accept")?1:0,database.<Integer>use(r->r.findAll().size()));
            if(action.equals("accept"))assertEquals("NORMAL",database.use(r->r.findByPlayer(b.id()).type()));
            verify(messages,never()).send(actor,"request-pending");
            // 完成者能释放自己的门闩，不影响后续新操作。
            var completed=new AtomicInteger();marriages.submit(live,r->null,ignored->completed.incrementAndGet());
            Objects.requireNonNull(workers.poll()).run();assertEquals(1,completed.get());
        }finally{
            finishOld.countDown();finishGap.countDown();
            executor.shutdown();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));
            if(running!=null)running.get(5,TimeUnit.SECONDS);
        }
    }

    @Test void rejectedWorkerSchedulingReleasesOnlyItsRequestAndAllowsRetry(){
        UUID live=alice.getUniqueId();var rejected=new AtomicReference<Throwable>();var commits=new AtomicInteger();
        doThrow(new RejectedExecutionException("expected test rejection")).when(scheduler).runAsync(any());
        marriages.submit(live,r->null,ignored->fail("未调度的请求不能执行"),rejected::set);
        assertNotNull(rejected.get());assertTrue(workers.isEmpty());owners();clearInvocations(messages);
        doAnswer(c->{workers.add(c.getArgument(0));return mock(TaskHandle.class);}).when(scheduler).runAsync(any());
        marriages.submit(live,r->null,ignored->commits.incrementAndGet());
        marriages.submit(live,r->null,ignored->fail("重试仍须防抖"));owners();
        verify(messages).send(alice,"busy");
        Objects.requireNonNull(workers.poll()).run();assertEquals(1,commits.get());
        marriages.submit(live,r->null,ignored->commits.incrementAndGet());
        Objects.requireNonNull(workers.poll()).run();assertEquals(2,commits.get());
    }
}
