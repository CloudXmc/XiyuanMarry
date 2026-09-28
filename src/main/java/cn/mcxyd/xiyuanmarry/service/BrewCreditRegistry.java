package cn.mcxyd.xiyuanmarry.service;
import java.util.*;
/** 药水只在真实酿造产生后获得一次计数资格；只存签名，不存ItemStack或Inventory。 */
public final class BrewCreditRegistry {
    public record Credit(UUID token,String signature,long expires){}
    private final Map<String,Credit> credits=new LinkedHashMap<>();
    public synchronized void produce(String slot,String signature,long now){
        credits.values().removeIf(c->c.expires()<=now);
        credits.remove(slot);
        if(credits.size()>=4096)credits.remove(credits.keySet().iterator().next());
        credits.put(slot,new Credit(UUID.randomUUID(),signature,now+1200000));
    }
    public synchronized Credit peek(String slot,String signature,long now){
        var c=credits.get(slot);if(c==null)return null;
        if(c.expires()<=now){credits.remove(slot);return null;}
        return c.signature().equals(signature)?c:null;
    }
    public synchronized boolean consume(String slot,UUID token,String signature,long now){
        var c=peek(slot,signature,now);if(c==null||!c.token().equals(token))return false;credits.remove(slot);return true;
    }
    public synchronized void clearBlock(String key){for(int i=0;i<3;i++)credits.remove(key+":"+i);}
    public synchronized void clear(){credits.clear();}
}
