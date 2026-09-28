package cn.mcxyd.xiyuanmarry.gui;
import cn.mcxyd.xiyuanmarry.scheduler.UnifiedScheduler;import org.bukkit.entity.Player;import org.bukkit.event.*;import org.bukkit.event.inventory.*;import org.bukkit.event.player.PlayerQuitEvent;import org.bukkit.event.entity.PlayerDeathEvent;
public final class GuiListener implements Listener {
    private final GuiFactory gui;private final UnifiedScheduler scheduler;
    public GuiListener(GuiFactory g,UnifiedScheduler s){gui=g;scheduler=s;}
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getView().getTopInventory().getHolder() instanceof XiyuanHolder h))return;e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p)||!h.owner().equals(p.getUniqueId())||e.getClick()!=ClickType.LEFT||e.getRawSlot()<0||e.getRawSlot()>=h.getInventory().getSize()||!h.click())return;
        var action=h.action(e.getRawSlot());if(action==null)return;
        scheduler.runEntity(p,()->{if(p.getOpenInventory().getTopInventory().getHolder()!=h)return;gui.activate(p,h,action);});
    }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof XiyuanHolder)e.setCancelled(true);}
    @EventHandler public void close(InventoryCloseEvent e){gui.cancel(e.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void open(InventoryOpenEvent e){gui.cancel(e.getPlayer().getUniqueId());}
    @EventHandler public void quit(PlayerQuitEvent e){gui.cancel(e.getPlayer().getUniqueId());}
    @EventHandler public void death(PlayerDeathEvent e){gui.cancel(e.getEntity().getUniqueId());}
}
