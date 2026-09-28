package cn.mcxyd.xiyuanmarry.model;
import java.util.*;
import java.nio.charset.StandardCharsets;
public final class IdentityResolver {
 private IdentityResolver(){}
 public static String key(String mode,boolean ignoreCase,UUID live,String name){return switch(mode){case "OFFLINE_NAME"->"name:"+(ignoreCase?name.toLowerCase(Locale.ROOT):name);case "ONLINE_UUID"->"uuid:"+live;default->throw new IllegalArgumentException("非法身份模式");};}
 /** 名称是持久身份；内部代理键不写回 Player，也不会替换正版 UUID。 */
 public static UUID storageId(String key,UUID live){return key.startsWith("uuid:")?live:UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));}
}

