package cn.mcxyd.xiyuanmarry.placeholder;

import cn.mcxyd.xiyuanmarry.XiyuanMarryPlugin;
import cn.mcxyd.xiyuanmarry.model.BondLevel;
import cn.mcxyd.xiyuanmarry.service.MarriageService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** PlaceholderAPI 适配层；不调用 OfflinePlayer#getPlayer()，只传入身份副本。 */
public final class MarriageExpansion extends PlaceholderExpansion {
    private final XiyuanMarryPlugin plugin;
    private final MarriageService marriages;
    private final PlaceholderResolver resolver = new PlaceholderResolver();
    private volatile List<BondLevel> levels = List.of();

    public MarriageExpansion(XiyuanMarryPlugin plugin, MarriageService marriages) {
        this.plugin = plugin;
        this.marriages = marriages;
        reloadLevels();
    }

    public void reloadLevels() {
        var config = plugin.configurationManager().config();
        List<BondLevel> next = new ArrayList<>(10);
        for (int i = 1; i <= 10; i++) {
            next.add(new BondLevel(i, config.getString("bond.levels." + i + ".name", "Lv." + i),
                    config.getLong("bond.levels." + i + ".required", 0)));
        }
        levels = List.copyOf(next);
    }

    @Override public @NotNull String getIdentifier() { return "mythicmarry"; }
    @Override public @NotNull String getAuthor() { return String.join(", ", plugin.getDescription().getAuthors()); }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return null;
        // OfflinePlayer 仅用于取得身份字段；后续完全使用不可变数据快照。
        var id = player.getUniqueId();
        var name = player.getName();
        return resolver.resolve(id, name, params, marriages.view(), levels, System.currentTimeMillis());
    }
}
