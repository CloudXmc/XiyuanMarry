package cn.mcxyd.xiyuanmarry.listener;
import cn.mcxyd.xiyuanmarry.service.PartnerTeleportService;
import org.bukkit.event.*;import org.bukkit.event.player.PlayerQuitEvent;import org.bukkit.event.entity.PlayerDeathEvent;
public final class PartnerLifecycleListener implements Listener {
    private final PartnerTeleportService teleports;
    public PartnerLifecycleListener(PartnerTeleportService teleports){this.teleports=teleports;}
    @EventHandler public void quit(PlayerQuitEvent e){teleports.leave(e.getPlayer().getUniqueId());}
    @EventHandler public void death(PlayerDeathEvent e){teleports.leave(e.getEntity().getUniqueId());}
}
