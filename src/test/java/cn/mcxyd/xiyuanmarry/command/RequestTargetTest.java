package cn.mcxyd.xiyuanmarry.command;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RequestTargetTest {
    @Test void omittedTargetKeepsProposalDefault() {
        assertEquals(RequestTarget.PROPOSAL,RequestTarget.parse(new String[0]));
        assertEquals(RequestTarget.PROPOSAL,RequestTarget.parse(new String[]{"proposal"}));
    }
    @Test void explicitInvitationSelectsWeddingRequest() {
        assertEquals(RequestTarget.INVITATION,RequestTarget.parse(new String[]{"invitation"}));
        assertEquals(RequestTarget.INVITATION,RequestTarget.parse(new String[]{"INVITATION"}));
    }
    @Test void invalidOrExtraArgumentsDoNotFallThroughToProposal() {
        assertThrows(IllegalArgumentException.class,()->RequestTarget.parse(new String[]{"invitaton"}));
        assertThrows(IllegalArgumentException.class,()->RequestTarget.parse(new String[]{"proposal","extra"}));
    }
}
