package cn.mcxyd.xiyuanmarry.placeholder;

import cn.mcxyd.xiyuanmarry.XiyuanMarryPlugin;
import cn.mcxyd.xiyuanmarry.service.MarriageService;

/** 仅 PlaceholderAPI 已启用时实例化，隔离可选依赖的类加载边界。 */
public final class PlaceholderApiHook implements AutoCloseable {
    private final MarriageExpansion expansion;

    public PlaceholderApiHook(XiyuanMarryPlugin plugin, MarriageService marriages) {
        expansion = new MarriageExpansion(plugin, marriages);
    }

    public void register() {
        expansion.register();
    }

    public void reload() {
        expansion.reloadLevels();
    }

    @Override public void close() {
        expansion.unregister();
    }
}
