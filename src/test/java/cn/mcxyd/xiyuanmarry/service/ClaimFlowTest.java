package cn.mcxyd.xiyuanmarry.service;
import cn.mcxyd.xiyuanmarry.command.*;
import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.model.*;
import cn.mcxyd.xiyuanmarry.repository.*;
import cn.mcxyd.xiyuanmarry.scheduler.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class ClaimFlowTest {
 @TempDir Path root;
 final Queue<Runnable> tasks=new ArrayDeque<>();
 final UnifiedScheduler scheduler=mock(UnifiedScheduler.class);
 final PlayerDirectory players=mock(PlayerDirectory.class);
 final MessageService messages=mock(MessageService.class);
 final Player player=mock(Player.class);
 final UUID id=UUID.randomUUID();
 final PlayerSnapshot actor=new PlayerSnapshot(id,id,"offline:alice","Alice",60,null,1);
 final List<String> sent=new ArrayList<>();
 DatabaseManager db; ConfigurationManager config; IoDispatcher io;
 MarriageService marriages; RewardService rewards; GiftService gifts; MarryCommand command; ClaimInboxService inbox; UUID seededGift;
 int queries;
 @BeforeEach void setup(){
  var plugin=mock(JavaPlugin.class);when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
  config=new ConfigurationManager(plugin);var files=new HashMap<String,YamlConfiguration>();
  for(String f:List.of("config.yml","rewards.yml","messages.yml"))files.put(f,YamlConfiguration.loadConfiguration(Path.of("src/main/resources",f).toFile()));
  var settings=DatabaseSettings.sqlite(root.resolve("inbox.db"));
  config.publish(new ConfigurationManager.Snapshot(UUID.randomUUID(),files,Map.of(),settings,null));db=new DatabaseManager(settings);
  when(player.hasPermission("marry.use")).thenReturn(true);when(players.capture(player)).thenReturn(actor);when(players.live(id)).thenReturn(actor);when(player.isOnline()).thenReturn(true);
  when(scheduler.runAsync(any())).thenAnswer(call->{queries++;tasks.add(call.getArgument(0));return mock(TaskHandle.class);});
  when(scheduler.runGlobal(any())).thenReturn(mock(TaskHandle.class));
  doAnswer(call->{Consumer<Player> action=call.getArgument(1);tasks.add(()->{if(player.isOnline())action.accept(player);});return null;}).when(scheduler).player(eq(id),any());
  doAnswer(call->{Consumer<Player> action=call.getArgument(1);Runnable unavailable=call.getArgument(2);tasks.add(()->{if(player.isOnline())action.accept(player);else unavailable.run();});return null;}).when(scheduler).player(eq(id),any(),any());
  doAnswer(call->{sent.add(call.getArgument(1));return null;}).when(messages).send(eq(player),anyString(),any(Object[].class));
  io=new IoDispatcher(scheduler);marriages=new MarriageService(plugin,config,messages,db,scheduler,io,players);
  rewards=new RewardService(marriages,config,scheduler,db,io,plugin.getLogger());gifts=new GiftService(marriages,scheduler);
  command=new MarryCommand(messages,players,marriages,null,null);
  MarriageSubcommands.register(command,marriages,null,players,messages,null,gifts,null,rewards);
  inbox=new ClaimInboxService(marriages,rewards,gifts,scheduler,db,config,messages);
  command.add(new ClaimSubcommand(inbox,rewards,gifts,players,messages));
 }
 @AfterEach void close(){if(inbox!=null)inbox.close();if(rewards!=null)rewards.close();if(marriages!=null)marriages.shutdown();if(io!=null)io.close();if(db!=null)db.close();tasks.clear();}
 void drain(){for(int n=0;n<100&&!tasks.isEmpty();n++)tasks.remove().run();assertTrue(tasks.isEmpty());}
 void invoke(String... arguments){var args=new String[arguments.length+1];args[0]="claim";System.arraycopy(arguments,0,args,1,arguments.length);command.onCommand(player,null,"marry",args);}
 void seed(){
  var reward=new RewardTicket(UUID.randomUUID(),id,"couple",1,1,"COMMITTED",7,0,0,10,List.of());
  seededGift=UUID.randomUUID();
  var gift=Map.of("id",seededGift,"sender",UUID.randomUUID(),"recipient",id,"created",1,"updated",1,"state","COMMITTED","kind","PARTNER","item","unused");
  db.use(r->{r.put("reward-inbox",reward.id().toString(),marriages.json().toJson(reward));r.put(GiftService.BUCKET,seededGift.toString(),marriages.json().toJson(gift));return null;});
 }
 @Test void mismatchedStorageAliasesAreNotListedAsAdditionalRewards(){
  seed();
  db.use(r->{for(String bucket:List.of("reward-inbox",GiftService.BUCKET)){
   var original=r.entries(bucket).values().iterator().next();r.put(bucket,UUID.randomUUID().toString(),original);
  }return null;});
  invoke();drain();
  assertEquals(1,Collections.frequency(sent,"reward-id"));assertEquals(1,Collections.frequency(sent,"gift-id"));
  assertEquals(2,db.<Integer>use(r->r.entries(GiftService.BUCKET).size()),"损坏行应保留");
 }
 @Test void recoveryOfMismatchedGiftCannotOverwriteValidGift(){
  seed();String original=db.use(r->r.get(GiftService.BUCKET,seededGift.toString()));
  var damaged=com.google.gson.JsonParser.parseString(original).getAsJsonObject();damaged.addProperty("state","RESERVED");
  UUID alias=UUID.randomUUID();db.use(r->{r.put(GiftService.BUCKET,alias.toString(),damaged.toString());return null;});
  invoke();drain();
  assertEquals(original,db.<String>use(r->r.get(GiftService.BUCKET,seededGift.toString())));
  assertEquals(damaged.toString(),db.<String>use(r->r.get(GiftService.BUCKET,alias.toString())));
  assertEquals(1,Collections.frequency(sent,"gift-id"));
 }
 @Test void mismatchedGiftCannotEnterClaimingOrReachItemDelivery(){
  seed();String original=db.use(r->r.get(GiftService.BUCKET,seededGift.toString()));
  UUID alias=UUID.randomUUID();db.use(r->{r.put(GiftService.BUCKET,alias.toString(),original);return null;});
  gifts.claim(actor,alias);drain();
  assertEquals(original,db.<String>use(r->r.get(GiftService.BUCKET,alias.toString())));
  assertEquals(original,db.<String>use(r->r.get(GiftService.BUCKET,seededGift.toString())));
  assertTrue(sent.contains("gift-missing"));assertFalse(sent.contains("gift-invalid"));
  verify(player,never()).getInventory();
 }
 @Test void oneRequestReadsBothInboxesWithoutSelfBusy(){seed();invoke();drain();assertTrue(sent.containsAll(List.of("reward-id","gift-id")),sent.toString());assertFalse(sent.contains("busy"));assertEquals(1,queries,"同一收件箱应在一次事务内读取");verify(players,times(1)).capture(player);}
 @Test void entityCallbackBeforeIoReturnsCannotSuppressGiftList(){
  // 模拟下一 tick 已开始、异步提交尚未退出 finally 的合法交错。
  doAnswer(call->{Consumer<Player> action=call.getArgument(1);action.accept(player);return null;}).when(scheduler).player(eq(id),any(),any());
  seed();invoke();drain();assertFalse(sent.contains("busy"),sent.toString());assertTrue(sent.contains("gift-id"),sent.toString());
 }
 @Test void rejectsExtraArgumentsBeforeClaiming(){invoke(UUID.randomUUID().toString(),"extra");drain();assertEquals(List.of("invalid-argument"),sent);assertEquals(0,queries);}
 @Test void emptyInboxReportsBothCategories(){invoke();drain();assertEquals(List.of("reward-none","gift-none"),sent);assertEquals(1,queries);}
 @Test void rewardOnlyStillReportsGiftCategory(){seed();db.use(r->{for(String key:r.entries(GiftService.BUCKET).keySet())r.remove(GiftService.BUCKET,key);return null;});invoke();drain();assertEquals(List.of("reward-pending","reward-id","gift-none"),sent);}
 @Test void giftOnlyStillReportsRewardCategory(){seed();db.use(r->{for(String key:r.entries("reward-inbox").keySet())r.remove("reward-inbox",key);return null;});invoke();drain();assertEquals(List.of("reward-none","gift-pending","gift-id"),sent);}
 @Test void reviewTicketsAreVisibleAndNotReopened(){
  seed();db.use(r->{for(String bucket:List.of("reward-inbox",GiftService.BUCKET))for(var entry:r.entries(bucket).entrySet()){
   var value=com.google.gson.JsonParser.parseString(entry.getValue()).getAsJsonObject();value.addProperty("state","CLAIMING");r.put(bucket,entry.getKey(),value.toString());
  }return null;});
  invoke();drain();assertEquals(List.of("reward-review-id","delivery-review"),sent);
  db.use(r->{for(String bucket:List.of("reward-inbox",GiftService.BUCKET))for(String value:r.entries(bucket).values())assertEquals("CLAIMING",com.google.gson.JsonParser.parseString(value).getAsJsonObject().get("state").getAsString());return null;});
 }
 @Test void otherPlayersPrivateInboxesAreNotDisplayed(){
  seed();db.use(r->{for(String bucket:List.of("reward-inbox",GiftService.BUCKET))for(var entry:r.entries(bucket).entrySet()){
   var value=com.google.gson.JsonParser.parseString(entry.getValue()).getAsJsonObject();value.addProperty("recipient",UUID.randomUUID().toString());r.put(bucket,entry.getKey(),value.toString());
  }return null;});invoke();drain();assertEquals(List.of("reward-none","gift-none"),sent);
 }
 @Test void reloadBetweenQueryAndReplyDropsOldSnapshot(){
  seed();invoke();tasks.remove().run();var old=config.snapshot();
  config.publish(new ConfigurationManager.Snapshot(UUID.randomUUID(),old.files(),old.menus(),old.database(),old.tasks()));
  drain();assertTrue(sent.isEmpty(),sent.toString());invoke();drain();assertTrue(sent.contains("gift-id"));
 }
 @Test void databaseSwitchAfterQueryDropsReplyAndPreservesOldData(){
  seed();invoke();tasks.remove().run();var original=config.snapshot().database();
  assertTrue(db.switchTo(DatabaseSettings.sqlite(root.resolve("new.db"))));drain();assertTrue(sent.isEmpty());
  assertTrue(db.<Boolean>use(r->r.entries("reward-inbox").isEmpty()&&r.entries(GiftService.BUCKET).isEmpty()));
  assertTrue(db.switchTo(original));invoke();drain();assertTrue(sent.containsAll(List.of("reward-id","gift-id")));
 }
 @Test void databaseSwitchBeforeQueryDoesNotDisplayNewDatabaseAsOldRequest(){
  seed();invoke();db.switchTo(DatabaseSettings.sqlite(root.resolve("before.db")));drain();assertTrue(sent.isEmpty());
 }
 @Test void closeDropsQueuedRepliesAndRejectsNewRequests(){
  seed();invoke();tasks.remove().run();inbox.close();drain();assertTrue(sent.isEmpty());invoke();drain();assertEquals(1,queries);
 }
 @Test void disconnectedPlayerReceivesNoQueuedReply(){seed();invoke();tasks.remove().run();when(player.isOnline()).thenReturn(false);drain();assertTrue(sent.isEmpty());}
 @Test void identityChangeDropsPreviousIdentitySnapshot(){
  seed();invoke();tasks.remove().run();when(players.live(id)).thenReturn(new PlayerSnapshot(id,UUID.randomUUID(),"other","Alice",60,null,1));drain();assertTrue(sent.isEmpty());
 }
 @Test void repeatedRequestIsBusyOnlyForRealOverlap(){
  seed();invoke();invoke();drain();assertEquals(1,queries);assertEquals(1,Collections.frequency(sent,"busy"));assertEquals(1,Collections.frequency(sent,"gift-id"));
  sent.clear();invoke();drain();assertFalse(sent.contains("busy"));assertTrue(sent.contains("gift-id"));
 }
 @Test void databaseFailureFinishesInboxReadWithoutSuccess(){
  var failures=new ArrayList<Throwable>();var replies=new ArrayList<InboxMessage>();
  inbox.load(actor,(p,items)->replies.addAll(items),failures::add);db.close();drain();
  assertEquals(1,failures.size());assertTrue(replies.isEmpty());assertTrue(sent.contains("internal-error"));
 }
 @Test void rejectedInboxReadInvokesFailureOnceAndAllowsRetry(){
  var failures=new ArrayList<Throwable>();
  doThrow(new IllegalStateException("test scheduler rejection")).when(scheduler).runAsync(any());
  inbox.load(actor,(p,items)->fail("Rejected read cannot succeed"),failures::add);drain();
  assertEquals(1,failures.size());assertEquals(List.of("busy"),sent);
  doAnswer(call->{tasks.add(call.getArgument(0));return mock(TaskHandle.class);}).when(scheduler).runAsync(any());
  sent.clear();invoke();drain();assertEquals(List.of("reward-none","gift-none"),sent);
 }
 @Test void overlappingInboxReadFinishesRejectedRequestOnly(){
  var failures=new ArrayList<Throwable>();
  invoke();inbox.load(actor,(p,items)->fail("Overlapping read cannot succeed"),failures::add);drain();
  assertEquals(1,failures.size());assertEquals(1,Collections.frequency(sent,"busy"));
  assertTrue(sent.containsAll(List.of("reward-none","gift-none")));
 }
 @Test void staleDatabaseReadNotifiesFailureWithoutReadingReplacement(){
  var failures=new ArrayList<Throwable>();
  inbox.load(actor,(p,items)->fail("Stale read cannot succeed"),failures::add);
  db.switchTo(DatabaseSettings.sqlite(root.resolve("replacement.db")));drain();
  assertEquals(1,failures.size());assertInstanceOf(DatabaseManager.StaleGenerationException.class,failures.getFirst());
 }
 @Test void reloadAfterQueryNotifiesFailureToReleaseGuiRequest(){
  var failures=new ArrayList<Throwable>();
  inbox.load(actor,(p,items)->fail("Stale reply cannot succeed"),failures::add);tasks.remove().run();
  var old=config.snapshot();config.publish(new ConfigurationManager.Snapshot(UUID.randomUUID(),old.files(),old.menus(),old.database(),old.tasks()));
  drain();assertEquals(1,failures.size());
 }
 @Test void retiredPlayerNotifiesFailureToReleaseGuiRequest(){
  var failures=new ArrayList<Throwable>();
  inbox.load(actor,(p,items)->fail("Offline reply cannot succeed"),failures::add);tasks.remove().run();
  when(player.isOnline()).thenReturn(false);drain();assertEquals(1,failures.size());
 }
 @Test void revokedPermissionOrDeathSuppressesPrivateInbox(){
  invoke();tasks.remove().run();when(player.hasPermission("marry.use")).thenReturn(false);drain();assertTrue(sent.isEmpty());
  when(player.hasPermission("marry.use")).thenReturn(true);invoke();tasks.remove().run();when(player.isDead()).thenReturn(true);drain();assertTrue(sent.isEmpty());
 }
 @Test void unavailableActorOrServiceFinishesWithoutSubmittingIo(){
  var failures=new ArrayList<Throwable>();
  inbox.load(null,(p,items)->fail("Missing actor cannot succeed"),failures::add);
  inbox.close();inbox.load(actor,(p,items)->fail("Closed service cannot succeed"),failures::add);
  assertEquals(2,failures.size());assertEquals(0,queries);
 }
 @Test void schedulingRejectionReleasesPlayerBusyStateForRetry(){
  seed();
  doThrow(new IllegalStateException("test scheduler unavailable")).when(scheduler).runAsync(any());
  assertDoesNotThrow(()->invoke()); drain(); assertTrue(sent.contains("busy"),sent.toString());
  doAnswer(call->{tasks.add(call.getArgument(0));return mock(TaskHandle.class);}).when(scheduler).runAsync(any());
  sent.clear();invoke();drain();assertFalse(sent.contains("busy"),sent.toString());
  assertTrue(sent.containsAll(List.of("reward-id","gift-id")),sent.toString());
 }
 @Test void invalidUuidDoesNotStartAnyWork(){invoke("invalid");drain();assertEquals(List.of("invalid-argument"),sent);assertEquals(0,queries);verify(players,never()).capture(any());}
 @Test void missingPlayerSnapshotCannotEnterClaimStateMachine(){
  when(players.capture(player)).thenReturn(null);
  assertDoesNotThrow(()->invoke()); drain();
  assertEquals(List.of("offline"),sent); assertEquals(0,queries);
  verify(player,never()).giveExp(anyInt()); verify(player,never()).getInventory();
 }
 @Test void helpAndCompletionRespectClaimPermissions(){
  var claim=command.commands().get("claim");assertInstanceOf(ClaimSubcommand.class,claim);
  assertEquals("no-permission",CommandAccess.rejection(claim,true,p->false));
  assertEquals("player-only",CommandAccess.rejection(claim,false,p->true));
  assertEquals(List.of("claim"),CommandAccess.visibleNames(command.commands().values(),true,p->true,"cla"));
  assertTrue(CommandAccess.visibleNames(command.commands().values(),true,p->false,"cla").isEmpty());
  assertNotNull(config.messages().getString("help-claim"));
 }
 @Test void giftClaimCapturesTokenAndGenerationBeforeEntityDelivery(){
  seed();var generation=db.generation();gifts.claim(actor,seededGift);assertEquals(1,queries);tasks.remove().run();
  assertTrue(db.<Boolean>use(r->{var raw=r.get(GiftService.BUCKET,seededGift.toString());return raw!=null&&raw.contains("CLAIMING")&&raw.contains("claimToken")&&raw.contains(generation.toString());}));
 }
 @Test void giftClaimFromOldGenerationCannotTouchNewDatabase(){
  seed();gifts.claim(actor,seededGift);tasks.remove().run();assertTrue(db.switchTo(DatabaseSettings.sqlite(root.resolve("gift-new.db"))));
  while(!tasks.isEmpty())tasks.remove().run();assertTrue(db.<Boolean>use(r->r.get(GiftService.BUCKET,seededGift.toString())==null));
 }
 @Test void missingSnapshotRestoresRewardWithoutGivingExperience(){
  seed();
  var ticket=db.use(r->marriages.json().fromJson(r.entries("reward-inbox").values().iterator().next(),RewardTicket.class));
  when(scheduler.runAsyncLater(any(),anyLong(),any())).thenReturn(mock(TaskHandle.class));
  doAnswer(call->{Consumer<Player> action=call.getArgument(1);tasks.add(()->action.accept(player));return null;}).when(scheduler).player(eq(id),any(),any());
  try(var claims=new RewardClaimService(marriages,config,db,io,scheduler,Logger.getAnonymousLogger())){
   claims.claim(actor,ticket.id(),ignored->{}); tasks.remove().run();
   when(players.capture(player)).thenReturn(null); assertDoesNotThrow(this::drain);
   verify(player,never()).giveExp(anyInt());
   assertEquals("COMMITTED",db.use(r->marriages.json().fromJson(r.get("reward-inbox",ticket.id().toString()),RewardTicket.class)).state());
   assertTrue(sent.contains("reward-claim-cancelled"));
  }
 }
 @Test void commitMarkerRunsBeforeFallibleViewRefresh(){
  var events=new ArrayList<String>();
  marriages.submit(null,r->{r.put("profiles","broken","invalid-json");return null;},x->events.add("committed"),x->events.add("failed"),()->events.add("marker"));
  tasks.remove().run();
  assertEquals(List.of("marker","committed"),events);
 }
 @Test void rollbackDoesNotRunCommitMarkerOrKeepWrittenData(){
  var events=new ArrayList<String>();
  marriages.submit(null,r->{r.put("test","rollback","no");throw new RuleViolation("gift-missing");},x->events.add("committed"),x->{assertInstanceOf(RuleViolation.class,x);assertTrue(TransactionRollbackException.confirmed(x));events.add("failed");},()->events.add("marker"));
  tasks.remove().run(); assertEquals(List.of("failed"),events);
  assertNull(db.use(r->r.get("test","rollback")));
 }
 @Test void shutdownDuringTransactionStillRunsCommitMarker(){
  var events=new ArrayList<String>();
  marriages.submit(null,r->{r.put("test","shutdown","ok");marriages.shutdown();return null;},x->events.add("committed"),x->events.add("failed"),()->events.add("marker"));
  tasks.remove().run(); assertEquals(List.of("marker"),events);
  assertEquals("ok",db.<String>use(r->r.get("test","shutdown")));
 }
 @Test void commitMarkerRunsOnlyAfterTransactionCommit(){
  var events=new ArrayList<String>();
  marriages.submit(null,r->{r.put("test","commit-marker","ok");return null;},x->events.add("committed"),x->events.add("failed"),()->events.add("marker"));
  assertEquals(1,queries);
  tasks.remove().run();
  assertEquals(List.of("marker","committed"),events);
  assertEquals("ok",db.<String>use(r->r.get("test","commit-marker")));
 }
}
