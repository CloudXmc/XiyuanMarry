package cn.mcxyd.xiyuanmarry.gui;
import cn.mcxyd.xiyuanmarry.message.TextRenderer;import org.bukkit.inventory.*;import org.bukkit.enchantments.Enchantment;
public final class IconFactory {
 private final TextRenderer text;public IconFactory(TextRenderer text){this.text=text;}
 public ItemStack create(GuiLayout.Icon icon,Object...values){var item=new ItemStack(icon.material());var meta=item.getItemMeta();meta.displayName(text.gui(text.format(icon.name(),values)));meta.lore(icon.lore().stream().map(line->text.gui(text.format(line,values))).toList());if(icon.model()!=0)meta.setCustomModelData(icon.model());if(icon.enchant())meta.setEnchantmentGlintOverride(true);if(icon.hideFlags())meta.addItemFlags(ItemFlag.values());if(icon.hideEnchant())meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);item.setItemMeta(meta);return item;}
}

