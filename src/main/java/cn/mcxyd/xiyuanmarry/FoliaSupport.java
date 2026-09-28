package cn.mcxyd.xiyuanmarry;

/** Folia 检测只在插件启动早期执行一次；业务代码只读取缓存结果。 */
public final class FoliaSupport {
    private static Boolean folia;
    private FoliaSupport() {}

    public static synchronized void detectOnce() {
        if (folia != null) return;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException ignored) {
            folia = false;
        }
    }

    public static boolean isFolia() {
        if (folia == null) detectOnce();
        return folia;
    }
}
