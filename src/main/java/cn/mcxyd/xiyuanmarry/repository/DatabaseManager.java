package cn.mcxyd.xiyuanmarry.repository;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.UUID;
/** 请求持有数据库代次租约；切换不复制旧数据，旧连接只在最后一个租约释放后关闭。 */
public final class DatabaseManager implements AutoCloseable {
 public static final class StaleGenerationException extends IllegalStateException {public StaleGenerationException(){super("数据库代次已改变");}}
 public record SwitchResult<T>(boolean switched,T value) {}
 private static final class Generation {
  final UUID id=UUID.randomUUID(); final MarriageRepository repository; final DatabaseSettings settings;
  int users; boolean retired; final AtomicBoolean disposed=new AtomicBoolean();
  Generation(DatabaseSettings s,java.util.function.Function<DatabaseSettings,MarriageRepository> factory){settings=s;repository=factory.apply(s);}
  void release(){boolean dispose=false;synchronized(this){if(users>0)users--;if(users==0&&retired)dispose=disposed.compareAndSet(false,true);}if(dispose)repository.close();}
  void retire(){boolean dispose=false;synchronized(this){retired=true;if(users==0)dispose=disposed.compareAndSet(false,true);}if(dispose)repository.close();}
 }
 private final Object switchLock=new Object();private final DatabaseSettings initial;private final java.util.function.Function<DatabaseSettings,MarriageRepository> factory;private Generation current;private boolean initialized,closed;
 public DatabaseManager(DatabaseSettings s){this(s,true,JdbcMarriageRepository::new);}
 /** 延迟构造连接池与迁移；插件启动时由 IO 队列调用，测试和嵌入式使用保持立即初始化兼容。 */
 public DatabaseManager(DatabaseSettings s,boolean initialize){this(s,initialize,JdbcMarriageRepository::new);}
 DatabaseManager(DatabaseSettings s,boolean initialize,java.util.function.Function<DatabaseSettings,MarriageRepository> factory){initial=java.util.Objects.requireNonNull(s);this.factory=java.util.Objects.requireNonNull(factory);if(initialize)initialize();}
 public void initialize(){
  // 建库只串行化初始化/切换；状态查询与停服不等待网络连接或表结构迁移。
  synchronized(switchLock){
   synchronized(this){if(closed)throw new IllegalStateException("数据库已关闭");if(initialized)return;}
   Generation candidate=new Generation(initial,factory);
   synchronized(this){if(!closed){current=candidate;initialized=true;return;}}
   // 停服先发生时，迟到的池只在当前 IO 线程销毁，不能持有管理器锁。
   candidate.retire();
   throw new IllegalStateException("数据库已关闭");
  }
 }
 private synchronized void requireReady(){if(closed)throw new IllegalStateException("数据库已关闭");if(!initialized)throw new IllegalStateException("数据库尚未初始化");}
 public synchronized UUID generation(){requireReady();return current.id;}
 public synchronized boolean initialized(){return initialized&&!closed;}
 public synchronized DatabaseSettings currentSettings(){requireReady();return current.settings;}
 public synchronized boolean isCurrent(UUID expected){return initialized&&!closed&&current.id.equals(expected);}
 public <T>T use(Function<MarriageRepository,T> action){return use(null,action);}
 /** 校验与租约获取在同一短锁内完成；SQL 不持有管理器锁，旧活动请求仍可完成。 */
 public <T>T use(UUID expected,Function<MarriageRepository,T> action){Generation g;synchronized(this){requireReady();if(expected!=null&&!current.id.equals(expected))throw new StaleGenerationException();g=current;synchronized(g){g.users++;}}try{return action.apply(g.repository);}finally{g.release();}}
 public boolean switchTo(DatabaseSettings s){return switchTo(s,r->null).switched();}
 /** 候选库先构造完整只读业务快照；失败时从未发布候选代次，旧请求和连接池保持有效。 */
 public <T> SwitchResult<T> switchTo(DatabaseSettings s,Function<MarriageRepository,T> validate){
  java.util.Objects.requireNonNull(validate);
  synchronized(switchLock){
   boolean unchanged;
   synchronized(this){requireReady();unchanged=current.settings.equals(s);}
   if(unchanged)return new SwitchResult<>(false,use(validate));
   var candidate=new Generation(s,factory);T value;
   try{value=validate.apply(candidate.repository);}
   catch(RuntimeException|Error failure){candidate.retire();throw failure;}
   Generation old;
   synchronized(this){old=closed?null:current;if(old!=null)current=candidate;}
   if(old==null){candidate.retire();throw new IllegalStateException("数据库已关闭");}
   old.retire();return new SwitchResult<>(true,value);
  }
 }
 @Override public void close(){Generation retiring;synchronized(this){if(closed)return;closed=true;retiring=current;current=null;}if(retiring!=null)retiring.retire();}
}

