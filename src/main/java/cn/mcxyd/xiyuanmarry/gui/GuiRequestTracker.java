package cn.mcxyd.xiyuanmarry.gui;
import java.util.*;
/** 仅存UUID与截止时间；有容量上限，关闭/退出/重载会显式清理。 */
public final class GuiRequestTracker {
    private record Pending(UUID token,long until) {}
    private final Map<UUID,Pending> pending=new HashMap<>();
    public synchronized UUID begin(UUID player,long now) {
        pending.entrySet().removeIf(e->e.getValue().until()<=now);
        if (!pending.containsKey(player)&&pending.size()>=1024) return null;
        UUID token=UUID.randomUUID();pending.put(player,new Pending(token,now+10000));return token;
    }
    /** 为读取操作提供防抖；已有请求仍由原回调负责收尾。 */
    public synchronized UUID beginIfAbsent(UUID player,long now) {
        pending.entrySet().removeIf(e->e.getValue().until()<=now);
        if (pending.containsKey(player)||pending.size()>=1024) return null;
        UUID token=UUID.randomUUID();pending.put(player,new Pending(token,now+10000));return token;
    }
    public synchronized boolean consume(UUID player,UUID token,long now) {
        var request=pending.get(player);
        if (request==null||!request.token().equals(token)) return false;
        pending.remove(player);return request.until()>now;
    }
    public synchronized void cancel(UUID player) {pending.remove(player);}
    public synchronized void clear() {pending.clear();}
}
