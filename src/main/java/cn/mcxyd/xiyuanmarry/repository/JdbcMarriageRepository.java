package cn.mcxyd.xiyuanmarry.repository;
import cn.mcxyd.xiyuanmarry.model.*;
import com.zaxxer.hikari.*;
import org.sqlite.SQLiteDataSource;
import com.mysql.cj.jdbc.MysqlDataSource;
import java.sql.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
/** SQL、事务和方言均绑定此连接池实例。业务只传递不可变数据。 */
public class JdbcMarriageRepository implements MarriageRepository {
 private final HikariDataSource pool;private final ThreadLocal<Connection> tx=new ThreadLocal<>();
 public JdbcMarriageRepository(DatabaseSettings s){
  HikariConfig cfg=new HikariConfig();cfg.setMaximumPoolSize(s.type().equals("SQLITE")?1:s.poolSize());cfg.setMinimumIdle(1);cfg.setConnectionTimeout(s.timeout());cfg.setInitializationFailTimeout(s.timeout());cfg.setPoolName("Xiyuan-"+UUID.randomUUID());
  try{if(s.type().equals("SQLITE")){Path p=s.file().toAbsolutePath();Files.createDirectories(p.getParent());var ds=new SQLiteDataSource();ds.setUrl("jdbc:sqlite:"+p);cfg.setDataSource(ds);cfg.setConnectionInitSql("PRAGMA busy_timeout=5000");}else{var ds=new MysqlDataSource();ds.setURL("jdbc:mysql://"+s.host()+":"+s.port()+"/"+s.database()+"?"+s.parameters());ds.setUser(s.username());ds.setPassword(s.password());cfg.setDataSource(ds);}}catch(Exception e){throw new IllegalStateException("数据库配置初始化失败",e);}
  pool=new HikariDataSource(cfg);try{migrate();}catch(RuntimeException e){pool.close();throw e;}
 }
 private interface SqlWork<T>{T run(Connection c)throws SQLException;}
 private <T>T sql(SqlWork<T> body){var current=tx.get();try{if(current!=null)return body.run(current);try(var c=pool.getConnection()){return body.run(c);}}catch(SQLException e){throw new IllegalStateException("数据库操作失败",e);}}
 @Override public synchronized <T>T transaction(Function<MarriageRepository,T> body){if(tx.get()!=null)return body.apply(this);return sql(c->{tx.set(c);try{return JdbcTransaction.execute(c,()->body.apply(this));}finally{tx.remove();}});}
 private void migrate(){sql(c->{try(var st=c.createStatement()){
  st.executeUpdate("CREATE TABLE IF NOT EXISTS marriages(player_one VARCHAR(36) PRIMARY KEY,player_two VARCHAR(36) NOT NULL UNIQUE,state VARCHAR(32) NOT NULL,type VARCHAR(16) NOT NULL,created_at BIGINT NOT NULL,divorce_at BIGINT NOT NULL DEFAULT 0,bond BIGINT NOT NULL DEFAULT 0)");
  st.executeUpdate("CREATE TABLE IF NOT EXISTS xym_members(player_id VARCHAR(36) PRIMARY KEY,relationship_id VARCHAR(36) NOT NULL)");
  st.executeUpdate("CREATE TABLE IF NOT EXISTS xym_metadata(bucket VARCHAR(48) NOT NULL,entry_key VARCHAR(180) NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(bucket,entry_key))");
  Set<String> cols=new HashSet<>();try(var rs=c.getMetaData().getColumns(null,null,"marriages",null)){while(rs.next())cols.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));}
  for(var col:List.of("relationship_id VARCHAR(36) NOT NULL DEFAULT ''","married_at BIGINT NOT NULL DEFAULT 0","total_bond BIGINT NOT NULL DEFAULT 0","shared_seconds BIGINT NOT NULL DEFAULT 0"))if(!cols.contains(col.split(" ")[0]))st.executeUpdate("ALTER TABLE marriages ADD COLUMN "+col);
 }return null;});
 transaction(r->{for(var m:findAll()){if(m.id().isEmpty()){String id=UUID.randomUUID().toString();update("UPDATE marriages SET relationship_id=?,married_at=?,total_bond=bond WHERE player_one=?",id,m.married()?m.createdAt():0,m.playerOne().toString());}var updated=findByPlayer(m.playerOne());for(var p:List.of(m.playerOne(),m.playerTwo())){String existing=scalar("SELECT relationship_id FROM xym_members WHERE player_id=?",p.toString());if(existing==null)update("INSERT INTO xym_members(player_id,relationship_id) VALUES(?,?)",p.toString(),updated.id());else if(!existing.equals(updated.id()))throw new IllegalStateException("旧数据库包含重复婚姻，停止加载并保留原数据");}}put("schema","version","2");return null;});
 }
 private void bind(PreparedStatement ps,Object...args)throws SQLException{for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);}
 private int update(String q,Object...args){return sql(c->{try(var ps=c.prepareStatement(q)){bind(ps,args);return ps.executeUpdate();}});}
 private String scalar(String q,Object...args){return sql(c->{try(var ps=c.prepareStatement(q)){bind(ps,args);try(var rs=ps.executeQuery()){return rs.next()?rs.getString(1):null;}}});}
 private MarriageRecord read(ResultSet rs)throws SQLException{return new MarriageRecord(UUID.fromString(rs.getString("player_one")),UUID.fromString(rs.getString("player_two")),MarriageState.valueOf(rs.getString("state")),rs.getString("type"),rs.getLong("created_at"),rs.getLong("divorce_at"),rs.getLong("bond"),rs.getString("relationship_id"),rs.getLong("married_at"),rs.getLong("total_bond"),rs.getLong("shared_seconds"));}
 @Override public MarriageRecord findByPlayer(UUID id){return sql(c->{try(var ps=c.prepareStatement("SELECT * FROM marriages WHERE player_one=? OR player_two=?")){bind(ps,id.toString(),id.toString());try(var rs=ps.executeQuery()){return rs.next()?read(rs):null;}}});}
 @Override public List<MarriageRecord> findAll(){return sql(c->{var out=new ArrayList<MarriageRecord>();try(var ps=c.prepareStatement("SELECT * FROM marriages");var rs=ps.executeQuery()){while(rs.next())out.add(read(rs));}return List.copyOf(out);});}
 private boolean create(UUID a,UUID b,String type,long at,MarriageState phase){return transaction(r->{if(a.equals(b)||findByPlayer(a)!=null||findByPlayer(b)!=null)return false;UUID one=a.toString().compareTo(b.toString())<0?a:b;UUID two=one.equals(a)?b:a;String rid=UUID.randomUUID().toString();update("INSERT INTO xym_members(player_id,relationship_id) VALUES(?,?)",one.toString(),rid);update("INSERT INTO xym_members(player_id,relationship_id) VALUES(?,?)",two.toString(),rid);update("INSERT INTO marriages(player_one,player_two,state,type,created_at,relationship_id,married_at) VALUES(?,?,?,?,?,?,?)",one.toString(),two.toString(),phase.name(),type,at,rid,phase==MarriageState.MARRIED?at:0L);return true;});}
 @Override public boolean createEngagement(UUID a,UUID b,String type,long at){return create(a,b,type,at,MarriageState.ENGAGED);}
 @Override public boolean createMarriage(UUID a,UUID b,String type,long at){return create(a,b,type,at,MarriageState.MARRIED);}
 @Override public boolean completeMarriage(UUID a,UUID b,String type,long at){return update("UPDATE marriages SET state=?,married_at=? WHERE state=? AND type=? AND ((player_one=? AND player_two=?) OR (player_one=? AND player_two=?))","MARRIED",at,"ENGAGED",type,a.toString(),b.toString(),b.toString(),a.toString())==1;}
 @Override public boolean requestDivorce(UUID id,long at){var m=findByPlayer(id);return m!=null&&update("UPDATE marriages SET state=?,divorce_at=? WHERE relationship_id=? AND state=?","DIVORCE_PENDING",at,m.id(),"MARRIED")==1;}
 @Override public boolean withdrawDivorce(UUID id){var m=findByPlayer(id);return m!=null&&update("UPDATE marriages SET state=?,divorce_at=0 WHERE relationship_id=? AND state=?","MARRIED",m.id(),"DIVORCE_PENDING")==1;}
 @Override public boolean deleteMarriage(UUID id){return transaction(r->{var m=findByPlayer(id);if(m==null)return false;put("history",m.id(),new com.google.gson.Gson().toJson(m));update("DELETE FROM xym_members WHERE relationship_id=?",m.id());return update("DELETE FROM marriages WHERE relationship_id=?",m.id())==1;});}
 @Override public boolean addBond(UUID id,long amount){if(amount<=0)return false;var m=findByPlayer(id);return m!=null&&m.married()&&update("UPDATE marriages SET bond=bond+?,total_bond=total_bond+? WHERE relationship_id=?",amount,amount,m.id())==1;}
 @Override public boolean setBond(UUID id,long amount){if(amount<0)return false;var m=findByPlayer(id);return m!=null&&update("UPDATE marriages SET bond=? WHERE relationship_id=?",amount,m.id())==1;}
 @Override public boolean addOnline(UUID id,long seconds){if(seconds<1)return false;var m=findByPlayer(id);return m!=null&&m.married()&&update("UPDATE marriages SET shared_seconds=shared_seconds+? WHERE relationship_id=?",seconds,m.id())==1;}
 @Override public int expireEngagements(long cutoff){return transaction(r->{int n=0;for(var m:findAll())if(m.state()==MarriageState.ENGAGED&&m.createdAt()<=cutoff){deleteMarriage(m.playerOne());n++;}return n;});}
 @Override public int completeDueDivorces(long now){return transaction(r->{int n=0;for(var m:findAll())if(m.state()==MarriageState.DIVORCE_PENDING&&m.divorceAt()<=now){deleteMarriage(m.playerOne());n++;}return n;});}
 @Override public String get(String bucket,String key){return scalar("SELECT payload FROM xym_metadata WHERE bucket=? AND entry_key=?",bucket,key);}
 @Override public Map<String,String> entries(String bucket){return sql(c->{var out=new HashMap<String,String>();try(var ps=c.prepareStatement("SELECT entry_key,payload FROM xym_metadata WHERE bucket=?")){bind(ps,bucket);try(var rs=ps.executeQuery()){while(rs.next())out.put(rs.getString(1),rs.getString(2));}}return Map.copyOf(out);});}
 @Override public void put(String bucket,String key,String value){if(update("UPDATE xym_metadata SET payload=? WHERE bucket=? AND entry_key=?",value,bucket,key)==0)update("INSERT INTO xym_metadata(bucket,entry_key,payload) VALUES(?,?,?)",bucket,key,value);}
 @Override public void remove(String bucket,String key){update("DELETE FROM xym_metadata WHERE bucket=? AND entry_key=?",bucket,key);}
 @Override public void close(){pool.close();}
 public boolean closed(){return pool.isClosed();}
}


