package cn.mcxyd.xiyuanmarry.repository;
import java.util.concurrent.atomic.*;
import java.util.function.Function;
import java.util.UUID;
/** 请求持有数据库代次租约；切换不复制旧数据，旧连接只在最后一个租约释放后关闭。 */
public final class DatabaseManager implements AutoCloseable {
 public static final class StaleGenerationException extends IllegalStateException {public StaleGenerationException(){super("数据库代次已改变");}}
 private static final class Generation {final UUID id=UUID.randomUUID();final JdbcMarriageRepository repository;final DatabaseSettings settings;int users;boolean retired;Generation(DatabaseSettings s){settings=s;repository=new JdbcMarriageRepository(s);}synchronized void release(){if(--users==0&&retired)repository.close();}synchronized void retire(){retired=true;if(users==0)repository.close();}}
 private Generation current;private boolean closed;
 public DatabaseManager(DatabaseSettings s){current=new Generation(s);}
 public synchronized UUID generation(){if(closed)throw new IllegalStateException("数据库已关闭");return current.id;}
 public synchronized boolean isCurrent(UUID expected){return !closed&&current.id.equals(expected);}
 public <T>T use(Function<MarriageRepository,T> action){return use(null,action);}
 /** 校验与租约获取在同一短锁内完成；SQL 不持有管理器锁，旧活动请求仍可完成。 */
 public <T>T use(UUID expected,Function<MarriageRepository,T> action){Generation g;synchronized(this){if(closed)throw new IllegalStateException("数据库已关闭");if(expected!=null&&!current.id.equals(expected))throw new StaleGenerationException();g=current;synchronized(g){g.users++;}}try{return action.apply(g.repository);}finally{g.release();}}
 public boolean switchTo(DatabaseSettings s){synchronized(this){if(closed)throw new IllegalStateException();if(current.settings.equals(s))return false;}var candidate=new Generation(s);Generation old;synchronized(this){if(closed){candidate.retire();throw new IllegalStateException();}old=current;current=candidate;}old.retire();return true;}
 @Override public synchronized void close(){if(!closed){closed=true;current.retire();}}
}

