package cn.mcxyd.xiyuanmarry.gui;
import java.util.UUID;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class GuiRequestTrackerTest {
    @Test void closingOrReplacingRequestPreventsOldCallbacks() {
        var tracker=new GuiRequestTracker();var player=UUID.randomUUID();
        var old=tracker.begin(player,0);var next=tracker.begin(player,1);
        assertFalse(tracker.consume(player,old,2));assertTrue(tracker.consume(player,next,2));
        var closed=tracker.begin(player,3);tracker.cancel(player);assertFalse(tracker.consume(player,closed,4));
    }
    @Test void timeoutAndReloadDiscardRequests() {
        var tracker=new GuiRequestTracker();var player=UUID.randomUUID();
        var old=tracker.begin(player,0);assertFalse(tracker.consume(player,old,10000));
        var reload=tracker.begin(player,10001);tracker.clear();assertFalse(tracker.consume(player,reload,10002));
    }
}
