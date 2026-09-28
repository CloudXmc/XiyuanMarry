package cn.mcxyd.xiyuanmarry.service;

public final class PartnerInteractionPolicy {
    private PartnerInteractionPolicy() {}
    public static String validateChat(String raw, int maxLength) {
        if (raw == null) return null;
        String value = raw.strip();
        if (value.isEmpty() || value.length() > maxLength) return null;
        return value;
    }
    public static boolean cooldownReady(long until, long now) {
        return until <= now;
    }
}
