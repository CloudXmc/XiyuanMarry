package cn.mcxyd.xiyuanmarry.listener;
import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;import cn.mcxyd.xiyuanmarry.service.*;import org.bukkit.event.*;import org.bukkit.event.player.*;import org.bukkit.event.entity.PlayerDeathEvent;import io.papermc.paper.event.player.AsyncChatEvent;import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
public final class PlayerLifecycleListener implements Listener {
 private final PlayerDirectory directory;private final WeddingService weddings;private final MarriageService marriages;
 public PlayerLifecycleListener(PlayerDirectory d,WeddingService w,MarriageService m){directory=d;weddings=w;marriages=m;}
 @EventHandler public void join(PlayerJoinEvent e){directory.join(e.getPlayer());}
 @EventHandler public void quit(PlayerQuitEvent e){var live=e.getPlayer().getUniqueId();var p=directory.live(live);if(p!=null)weddings.leave(live);directory.leave(live);marriages.leave(live,marriages.view().profiles().values().stream().filter(x->x.liveId().equals(live)).map(x->x.id()).findFirst().orElse(live));}
 @EventHandler public void death(PlayerDeathEvent e){weddings.leave(e.getEntity().getUniqueId());}
 @EventHandler public void chat(AsyncChatEvent e){String text=PlainTextComponentSerializer.plainText().serialize(e.message());String oath=configuredOath();if(!text.equals(oath)||!weddings.pending(e.getPlayer().getUniqueId()))return;e.setCancelled(true);weddings.oathFromChat(e.getPlayer().getUniqueId());}
 private String configuredOath(){return weddings.oathText();}
}



