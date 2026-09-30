package cn.mcxyd.xiyuanmarry.service;
import cn.mcxyd.xiyuanmarry.model.RewardTicket;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RewardInboxTest {
    RewardTicket ticket(UUID owner, String state) { return new RewardTicket(UUID.randomUUID(), owner, "couple", 1, 1, state, 7, 0, 1, 2, List.of()); }
    @Test void interruptedAndReviewedTicketsRemainVisibleOnlyToOwner() {
        UUID owner = UUID.randomUUID(); var ready = ticket(owner, "COMMITTED");
        var interrupted = ticket(owner, "CLAIMING"); var review = ticket(owner, "REVIEW");
        var inbox = RewardInbox.forRecipient(List.of(ready, interrupted, review, ticket(UUID.randomUUID(), "COMMITTED")), owner);
        assertEquals(List.of(ready), inbox.available());
        assertEquals(Set.of(interrupted.id(), review.id()), Set.copyOf(inbox.review()));
        assertThrows(UnsupportedOperationException.class, () -> inbox.review().clear());
    }
    @Test void unknownStateIsNotSilentlyReportedAsEmpty() {
        UUID owner = UUID.randomUUID(); var old = ticket(owner, "UNKNOWN");
        var inbox = RewardInbox.forRecipient(List.of(old), owner);
        assertTrue(inbox.available().isEmpty()); assertEquals(List.of(old.id()), inbox.review());
    }

    @Test void incompleteTicketIsIgnoredByInbox() {
        UUID owner=UUID.randomUUID();
        var malformed=mock(RewardTicket.class);
        when(malformed.id()).thenReturn(UUID.randomUUID());
        when(malformed.state()).thenReturn("COMMITTED");
        var inbox=assertDoesNotThrow(() -> RewardInbox.forRecipient(List.of(malformed),owner));
        assertTrue(inbox.available().isEmpty());
    }
}
