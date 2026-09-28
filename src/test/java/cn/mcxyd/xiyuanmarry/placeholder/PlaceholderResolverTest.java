package cn.mcxyd.xiyuanmarry.placeholder;

import cn.mcxyd.xiyuanmarry.model.BondLevel;
import cn.mcxyd.xiyuanmarry.model.MarriageRecord;
import cn.mcxyd.xiyuanmarry.model.MarriageState;
import cn.mcxyd.xiyuanmarry.model.PlayerProfile;
import cn.mcxyd.xiyuanmarry.service.MarriageService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlaceholderResolverTest {
    private final PlaceholderResolver resolver = new PlaceholderResolver();
    private final UUID one = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID two = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID liveOne = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private final UUID liveTwo = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private final List<BondLevel> levels = List.of(new BondLevel(1, "倾心", 0), new BondLevel(2, "相知", 100));
    private final MarriageRecord married = new MarriageRecord(one, two, MarriageState.MARRIED, "NORMAL",
            0, 0, 150, "pair-a", 1_700_000_000_000L, 300, 7200);
    private final MarriageService.View view = new MarriageService.View(
            Map.of(one, married, two, married),
            Map.of(one, new PlayerProfile(one, "name:alice", "Alice", liveOne),
                    two, new PlayerProfile(two, "name:bob", "Bob", liveTwo)),
            List.of(married), Map.of());

    @Test void resolvesMarriageValuesUsingOfflineNameIdentity() {
        assertEquals("已婚", resolve(liveOne, "Alice", "status"));
        assertEquals("Bob", resolve(liveOne, "Alice", "partner"));
        assertEquals("2", resolve(liveOne, "Alice", "bond_level"));
        assertEquals("相知", resolve(liveOne, "Alice", "bond_level_name"));
        assertEquals("150", resolve(liveOne, "Alice", "bond_exp"));
        assertEquals("2", resolve(liveOne, "Alice", "marriage_days"));
    }

    @Test void resolvesBoardFieldsAndPersonalRank() {
        assertEquals("Alice ♥ Bob", resolve(liveOne, "Alice", "rank_bond_1_name"));
        assertEquals("2", resolve(liveOne, "Alice", "rank_bond_1_level"));
        assertEquals("2", resolve(liveOne, "Alice", "rank_duration_1_days"));
        assertEquals("7200", resolve(liveOne, "Alice", "rank_online_1_online"));
        assertEquals("300", resolve(liveOne, "Alice", "rank_total_1_total"));
        assertEquals("1", resolve(liveOne, "Alice", "my_rank_bond"));
        assertEquals("", resolve(liveOne, "Alice", "rank_bond_2_name"));
    }

    @Test void unknownVariableRemainsUnresolvedAndUnknownPlayerIsSingle() {
        assertNull(resolve(liveOne, "Alice", "unsupported"));
        assertEquals("未婚", resolve(UUID.randomUUID(), "Unknown", "status"));
    }

    private String resolve(UUID id, String name, String parameter) {
        return resolver.resolve(id, name, parameter, view, levels, 1_700_172_800_000L);
    }
}
