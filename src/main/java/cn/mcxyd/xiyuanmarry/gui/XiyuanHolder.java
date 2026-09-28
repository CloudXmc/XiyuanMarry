package cn.mcxyd.xiyuanmarry.gui;
import org.bukkit.inventory.*;import java.util.*;
/** 会话随玩家关闭界面释放；无静态玩家或Inventory引用。 */
public final class XiyuanHolder implements InventoryHolder {
 public record Action(String key,String value){}
 private final UUID owner,generation;private final String page,mode;private final int index;private final Map<Integer,Action> actions;private Inventory inventory;private long clicked;
 public XiyuanHolder(UUID owner,UUID generation,String page,String mode,int index,Map<Integer,Action> actions){this.owner=owner;this.generation=generation;this.page=page;this.mode=mode;this.index=index;this.actions=Map.copyOf(actions);}
 public void attach(Inventory inventory){if(this.inventory!=null)throw new IllegalStateException();this.inventory=inventory;}
 public UUID owner(){return owner;}public UUID generation(){return generation;}public String page(){return page;}public String mode(){return mode;}public int index(){return index;}public Action action(int slot){return actions.get(slot);}public boolean click(){long now=System.nanoTime();if(now-clicked<300000000)return false;clicked=now;return true;}
 @Override public Inventory getInventory(){return Objects.requireNonNull(inventory);}
}

