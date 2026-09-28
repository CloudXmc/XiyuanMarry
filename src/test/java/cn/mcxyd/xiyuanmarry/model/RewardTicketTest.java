package cn.mcxyd.xiyuanmarry.model;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RewardTicketTest {
    @Test void oldAnniversaryTicketWithoutRankRemainsClaimable() {
        var json = new Gson().toJson(Map.of("id", UUID.randomUUID(), "recipient", UUID.randomUUID(),
                "state", "COMMITTED", "anniversaryDays", 7, "money", 500, "experience", 100));
        var ticket = new Gson().fromJson(json, RewardTicket.class);
        assertEquals(0, ticket.rank());
        assertEquals(7, ticket.anniversaryDays());
        assertEquals(500, ticket.money());
        assertEquals("COMMITTED", ticket.state());
        assertEquals(List.of(), ticket.commands());
    }
    @Test void stateTransitionPreservesWeeklyRewardSnapshot() {
        var commands = new ArrayList<>(List.of("say {player}"));
        var ticket = new RewardTicket(UUID.randomUUID(), UUID.randomUUID(), "relation", 1, 1, "COMMITTED", 0, 3, 1500, 300, commands);
        commands.clear();
        var decoded = new Gson().fromJson(new Gson().toJson(ticket), RewardTicket.class).withState("CLAIMING", 2);
        assertEquals(3, decoded.rank());
        assertEquals(1500, decoded.money());
        assertEquals(List.of("say {player}"), decoded.commands());
        assertEquals("CLAIMING", decoded.state());
        assertEquals(ticket.id(), decoded.id());
    }
}
