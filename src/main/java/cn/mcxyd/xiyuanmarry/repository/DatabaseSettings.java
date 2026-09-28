package cn.mcxyd.xiyuanmarry.repository;
import java.nio.file.Path;
public record DatabaseSettings(String type,Path file,String host,int port,String database,String username,String password,String parameters,int poolSize,long timeout){
 public DatabaseSettings {if(!type.equals("SQLITE")&&!type.equals("MYSQL"))throw new IllegalArgumentException("数据库类型无效");if(port<1||port>65535||poolSize<1||poolSize>32||timeout<1000||timeout>60000)throw new IllegalArgumentException("连接池参数无效");}
 public static DatabaseSettings sqlite(Path path){return new DatabaseSettings("SQLITE",path.toAbsolutePath(),"",3306,"","","","",1,5000);}
 @Override public String toString(){return "DatabaseSettings[type="+type+",credentials=REDACTED]";}
}

