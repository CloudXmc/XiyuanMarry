package cn.mcxyd.xiyuanmarry.service;

import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.model.*;
import cn.mcxyd.xiyuanmarry.repository.*;
import cn.mcxyd.xiyuanmarry.scheduler.UnifiedScheduler;
import com.google.gson.Gson;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeddingInvitationFlowTest {
    @TempDir Path root;
    final MarriageService marriages=mock(MarriageService.class);
    final Gson json=new Gson();
    final UUID guestId=UUID.randomUUID();
    final PlayerSnapshot guest=new PlayerSnapshot(guestId,guestId,"guest","Guest",60,null,1);
    SqliteMarriageRepository repository;
    WeddingService weddings;
    @BeforeEach void setup(){
        repository=new SqliteMarriageRepository(root.resolve("invitations.db"));
        when(marriages.json()).thenReturn(json);
        when(marriages.setting("marriage.engagement-hours",48)).thenReturn(48L);
        doAnswer(call->{Function<MarriageRepository,Object> work=call.getArgument(1);Consumer<Object> done=call.getArgument(2);done.accept(repository.transaction(work));return null;})
                .when(marriages).submitPlayer(any(),any(),any());
        weddings=new WeddingService(marriages,mock(ConfigurationManager.class),mock(UnifiedScheduler.class));
    }
    @AfterEach void close(){weddings.clear();repository.close();}
    MarriageRecord invite(boolean expired){
        UUID one=UUID.randomUUID(),two=UUID.randomUUID();
        repository.createEngagement(one,two,"WEDDING",System.currentTimeMillis()-(expired?49*3600000L:0));
        var marriage=repository.findByPlayer(one);
        put(marriage.id(),WeddingPlan.empty().invite(guestId,new WeddingPlan.Invite(false,System.currentTimeMillis()+60000)));
        return marriage;
    }
    void put(String id,WeddingPlan plan){repository.put("weddings",id,json.toJson(plan));}
    WeddingPlan plan(String id){return json.fromJson(repository.get("weddings",id),WeddingPlan.class);}
    @Test void completedWeddingCannotAcceptOutstandingInvitation(){
        var marriage=invite(false);repository.completeMarriage(marriage.playerOne(),marriage.playerTwo(),"WEDDING",System.currentTimeMillis());
        assertThrows(RuleViolation.class,()->weddings.respond(guest,true));
        assertFalse(plan(marriage.id()).invites().get(guestId).accepted());
    }
    @Test void expiredEngagementCannotAcceptUnexpiredInvitation(){
        var marriage=invite(true);
        assertThrows(RuleViolation.class,()->weddings.respond(guest,true));
        assertFalse(plan(marriage.id()).invites().get(guestId).accepted());
    }
    @Test void staleInvitationDoesNotMakeValidRequestAmbiguous(){
        var stale=invite(true);var valid=invite(false);
        assertDoesNotThrow(()->weddings.respond(guest,true));
        assertFalse(plan(stale.id()).invites().get(guestId).accepted());
        assertTrue(plan(valid.id()).invites().get(guestId).accepted());
    }
    @Test void choosingOneOfMultipleInvitationsLeavesTheOthersUntouched(){
        var first=invite(false);var second=invite(false);
        weddings.respond(guest,second.id(),true);
        assertFalse(plan(first.id()).invites().get(guestId).accepted());
        assertTrue(plan(second.id()).invites().get(guestId).accepted());
        assertEquals(List.of(first.id()),weddings.pendingInvitations(repository,guestId).stream().map(WeddingInvitationService.Invitation::weddingId).toList());
    }
    @Test void decliningOneInvitationDoesNotDeclineOthers(){
        var first=invite(false);var second=invite(false);
        weddings.respond(guest,second.id(),false);
        assertEquals(0,plan(second.id()).invites().get(guestId).expires());
        assertTrue(plan(first.id()).invites().get(guestId).expires()>System.currentTimeMillis());
    }
    @Test void duplicateAcceptanceAndForeignIdsAreRejected(){
        var first=invite(false);weddings.respond(guest,first.id(),true);
        assertThrows(RuleViolation.class,()->weddings.respond(guest,first.id(),true));
        assertThrows(RuleViolation.class,()->weddings.respond(guest,UUID.randomUUID().toString(),false));
    }
    @Test void acceptedInvitationRemainsValidUntilEngagementEnds(){
        var marriage=invite(false);
        weddings.respond(guest,marriage.id(),true);
        long engagementDeadline=marriage.createdAt()+48L*3600000L;
        var accepted=plan(marriage.id());
        assertEquals(engagementDeadline,accepted.invites().get(guestId).expires());
        assertTrue(WeddingGiftPolicy.canSend(accepted,guestId,marriage,marriage.createdAt()+25L*3600000L));
        assertTrue(WeddingGiftPolicy.canSend(accepted,guestId,marriage,engagementDeadline-1));
        assertFalse(WeddingGiftPolicy.canSend(accepted,guestId,marriage,engagementDeadline));
        assertTrue(weddings.pendingInvitations(repository,guestId).isEmpty());
    }
    @Test void acceptedInviteCannotOutliveShorterEngagement(){
        when(marriages.setting("marriage.engagement-hours",48)).thenReturn(1L);
        var marriage=invite(false);
        put(marriage.id(),WeddingPlan.empty().invite(guestId,new WeddingPlan.Invite(false,
                marriage.createdAt()+24L*3600000L)));
        weddings.respond(guest,marriage.id(),true);
        assertEquals(marriage.createdAt()+3600000L,plan(marriage.id()).invites().get(guestId).expires());
    }
    @Test void expiredUnansweredInviteIsNotRevivedByAcceptance(){
        var marriage=invite(false);
        put(marriage.id(),WeddingPlan.empty().invite(guestId,new WeddingPlan.Invite(false,System.currentTimeMillis()-1000)));
        String before=repository.get("weddings",marriage.id());
        assertThrows(RuntimeException.class,()->weddings.respond(guest,marriage.id(),true));
        assertEquals(before,repository.get("weddings",marriage.id()));
    }
    @Test void acceptingOneInvitePreservesOtherGuestsAndPoints(){
        var marriage=invite(false);var other=UUID.randomUUID();
        var point=new PlayerSnapshot.Point(UUID.randomUUID(),12,70,24,0,0);
        var before=plan(marriage.id()).point("location",point)
                .invite(other,new WeddingPlan.Invite(false,System.currentTimeMillis()+60000));
        put(marriage.id(),before);weddings.respond(guest,marriage.id(),true);
        var after=plan(marriage.id());
        assertEquals(before.points(),after.points());assertEquals(before.invites().get(other),after.invites().get(other));
    }
    @Test void resendingAcceptedInviteCannotResetGuestAcceptance(){
        UUID one=UUID.randomUUID(),two=UUID.randomUUID();
        repository.createEngagement(one,two,"WEDDING",System.currentTimeMillis());
        var marriage=repository.findByPlayer(one);
        var accepted=new WeddingPlan.Invite(true,marriage.createdAt()+48L*3600000L);
        put(marriage.id(),WeddingPlan.empty().invite(guestId,accepted));
        var actor=new PlayerSnapshot(one,one,"one","One",60,null,1);
        assertThrows(RuleViolation.class,()->weddings.invite(actor,guest));
        assertEquals(accepted,plan(marriage.id()).invites().get(guestId));
    }
    @Test void acceptedDeadlineSurvivesRepositoryRestart(){
        var marriage=invite(false);weddings.respond(guest,marriage.id(),true);
        var before=plan(marriage.id());repository.close();
        repository=new SqliteMarriageRepository(root.resolve("invitations.db"));
        assertEquals(before,plan(marriage.id()));
        assertTrue(WeddingGiftPolicy.canSend(plan(marriage.id()),guestId,marriage,marriage.createdAt()+25L*3600000L));
    }
    @Test void anotherGuestCannotReadOrRespondToAnInvitation(){
        var first=invite(false);UUID other=UUID.randomUUID();
        assertTrue(weddings.pendingInvitations(repository,other).isEmpty());
        var actor=new PlayerSnapshot(other,other,"other","Other",60,null,1);
        assertThrows(RuleViolation.class,()->weddings.respond(actor,first.id(),true));
    }
    @Test void runningCeremoniesDoNotAcceptLateReplies(){
        var first=invite(false);
        var invitations=new WeddingInvitationService(marriages,id->id.equals(first.id()));
        assertTrue(invitations.pending(repository,guestId).isEmpty());
        assertThrows(RuleViolation.class,()->invitations.respond(guest,first.id(),true));
    }
    @Test void malformedWeddingPlanIsSkippedWithoutBreakingInbox(){
        var marriage=invite(false);
        repository.put("weddings",marriage.id(),"not-json");
        assertDoesNotThrow(() -> weddings.pendingInvitations(repository,guestId));
        assertTrue(weddings.pendingInvitations(repository,guestId).isEmpty());
    }
}
