package cn.mcxyd.xiyuanmarry.gui;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import java.util.*;

public record GuiLayout(String title, List<String> rows, Map<Character, Icon> icons, SoundSettings sounds) {
 public record SoundSettings(String open, String click, String close, float volume, float pitch) {
  public SoundSettings { open = open == null ? "minecraft:block.chest.open" : open; click = click == null ? "minecraft:ui.button.click" : click; close = close == null ? "minecraft:block.chest.close" : close; }
 }
 public record Icon(String name, Material material, List<String> lore, String action, boolean enchant, int model, boolean hideFlags, boolean hideEnchant, String returnCommand) {}
 public GuiLayout { rows = List.copyOf(rows); icons = Map.copyOf(icons); }
 public int size() { return rows.size() * 9; }
 public List<Integer> dynamicSlots() { var out = new ArrayList<Integer>(); for (int i=0;i<size();i++) if (rows.get(i/9).charAt(i%9)=='D') out.add(i); return List.copyOf(out); }
 public static GuiLayout parse(ConfigurationSection y) { return parse(y, mat -> true); }
 public static GuiLayout parse(ConfigurationSection y, java.util.function.Predicate<Material> itemMaterial) {
  var rows=y.getStringList("layout"); if(rows.isEmpty()||rows.size()>6||rows.stream().anyMatch(r->r.length()!=9)) throw new IllegalArgumentException("GUI 布局必须1–6行，每行9字符");
  var source=y.getConfigurationSection("icons"); if(source==null||source.contains("A")) throw new IllegalArgumentException("GUI A是固定空槽，不可定义图标或动作");
  var map=new HashMap<Character,Icon>();
  for(String k:source.getKeys(false)) { if(k.length()!=1) throw new IllegalArgumentException("图标键必须是一个字符"); var s=source.getConfigurationSection(k); if(s==null) throw new IllegalArgumentException("图标必须是配置节"); Material mat=Material.matchMaterial(s.getString("material","")); if(mat==null||mat==Material.AIR||mat==Material.CAVE_AIR||mat==Material.VOID_AIR||!itemMaterial.test(mat)) throw new IllegalArgumentException("无效图标材质: "+k); var lore=s.getStringList("lore"); if(k.charAt(0)!='#'&&(lore.size()<2||s.getString("action","").isBlank())) throw new IllegalArgumentException("功能图标需要教学lore和action"); map.put(k.charAt(0),new Icon(s.getString("name",""),mat,List.copyOf(lore),s.getString("action",""),s.getBoolean("isEnchant"),s.getInt("custom-model-data"),s.getBoolean("hideFlag",true),s.getBoolean("hideEnchant",true),s.getString("return-command","/mc"))); }
  for(String row:rows) for(char ch:row.toCharArray()) if(ch!='A'&&!map.containsKey(ch)) throw new IllegalArgumentException("缺少GUI图标: "+ch);
  var s=y.getConfigurationSection("sounds"); var sounds=new SoundSettings(s==null?null:s.getString("open"),s==null?null:s.getString("click"),s==null?null:s.getString("close"),s==null?1.0f:(float)s.getDouble("volume",1.0),s==null?1.0f:(float)s.getDouble("pitch",1.0)); if(!Float.isFinite(sounds.volume())||!Float.isFinite(sounds.pitch())||sounds.volume()<=0||sounds.volume()>4||sounds.pitch()<=0||sounds.pitch()>2) throw new IllegalArgumentException("GUI 音效音量和音调超出范围");
  return new GuiLayout(y.getString("title",""),rows,map,sounds);
 }
}
