package cn.mcxyd.xiyuanmarry.placeholder;

import cn.mcxyd.xiyuanmarry.model.BondLevel;
import cn.mcxyd.xiyuanmarry.model.MarriageRecord;
import cn.mcxyd.xiyuanmarry.model.MarriageState;
import cn.mcxyd.xiyuanmarry.model.PlayerProfile;
import cn.mcxyd.xiyuanmarry.service.MarriageService;
import cn.mcxyd.xiyuanmarry.service.RankingService;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 只读取不可变婚姻视图；可由 PlaceholderAPI 的异步上下文调用。 */
public final class PlaceholderResolver {
    private static final Pattern RANK = Pattern.compile("rank_(bond|duration|online|total)_([1-9][0-9]{0,3})_(name|level|days|bond|online|total)");
    private static final Pattern MY_RANK = Pattern.compile("my_rank_(bond|duration|online|total)");
    private final RankingService ranking = new RankingService();

    public String resolve(UUID playerId, String playerName, String parameter,
                          MarriageService.View view, List<BondLevel> levels, long now) {
        if (parameter == null || view == null) return null;
        String key = parameter.toLowerCase(Locale.ROOT);
        PlayerProfile profile = profile(playerId, playerName, view);
        UUID identity = profile == null ? playerId : profile.id();
        MarriageRecord marriage = identity == null ? null : view.byPlayer().get(identity);

        switch (key) {
            case "status": return status(marriage);
            case "partner": return marriage == null ? "" : name(marriage.partnerOf(identity), view);
            case "bond_level": return marriage == null || !marriage.married() ? "0" : Integer.toString(level(marriage.bond(), levels));
            case "bond_level_name": return marriage == null || !marriage.married() ? "" : title(level(marriage.bond(), levels), levels);
            case "bond_exp": return marriage == null || !marriage.married() ? "0" : Long.toString(marriage.bond());
            case "marriage_days": return marriage == null || !marriage.married() ? "0" : Long.toString(RankingService.days(marriage, now));
            default: break;
        }

        Matcher personal = MY_RANK.matcher(key);
        if (personal.matches()) {
            if (marriage == null || !marriage.married()) return "0";
            List<MarriageRecord> ranked = ranking.rank(view.couples(), personal.group(1), now,
                    bond -> level(bond, levels), 1000);
            for (int i = 0; i < ranked.size(); i++) if (ranked.get(i).id().equals(marriage.id())) return Integer.toString(i + 1);
            return "0";
        }

        Matcher rankMatch = RANK.matcher(key);
        if (!rankMatch.matches()) return null;
        int position = Integer.parseInt(rankMatch.group(2));
        List<MarriageRecord> ranked = ranking.rank(view.couples(), rankMatch.group(1), now,
                bond -> level(bond, levels), 1000);
        if (position > ranked.size()) return "";
        MarriageRecord entry = ranked.get(position - 1);
        return switch (rankMatch.group(3)) {
            case "name" -> name(entry.playerOne(), view) + " ♥ " + name(entry.playerTwo(), view);
            case "level" -> Integer.toString(level(entry.bond(), levels));
            case "days" -> Long.toString(RankingService.days(entry, now));
            case "bond" -> Long.toString(entry.bond());
            case "online" -> Long.toString(entry.sharedSeconds());
            case "total" -> Long.toString(entry.totalBond());
            default -> null;
        };
    }

    private static PlayerProfile profile(UUID playerId, String playerName, MarriageService.View view) {
        if (playerId != null) {
            PlayerProfile byIdentity = view.profiles().get(playerId);
            if (byIdentity != null) return byIdentity;
            for (PlayerProfile value : view.profiles().values()) if (playerId.equals(value.liveId())) return value;
        }
        if (playerName != null && !playerName.isBlank()) {
            for (PlayerProfile value : view.profiles().values()) if (value.name().equalsIgnoreCase(playerName)) return value;
        }
        return null;
    }

    private static String status(MarriageRecord marriage) {
        if (marriage == null) return "未婚";
        if (marriage.state() == MarriageState.ENGAGED) return "订婚中";
        return marriage.married() ? "已婚" : "未婚";
    }

    private static String name(UUID id, MarriageService.View view) {
        PlayerProfile profile = view.profiles().get(id);
        return profile == null || profile.name() == null || profile.name().isBlank() ? id.toString() : profile.name();
    }

    private static int level(long bond, List<BondLevel> levels) {
        return levels.stream().filter(level -> bond >= level.required())
                .max(Comparator.comparingInt(BondLevel::level)).map(BondLevel::level).orElse(1);
    }

    private static String title(int level, List<BondLevel> levels) {
        return levels.stream().filter(value -> value.level() == level).map(BondLevel::name).findFirst().orElse("");
    }
}
