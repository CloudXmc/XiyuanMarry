package cn.mcxyd.xiyuanmarry.service;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class BrewCreditRegistryTest {
    @Test void onlyActualBatchCanBeConsumedOnce(){
        var r=new BrewCreditRegistry();assertNull(r.peek("block:0","potion",0));
        r.produce("block:0","potion",10);var token=r.peek("block:0","potion",20).token();
        assertNull(r.peek("block:0","water",20));
        assertTrue(r.consume("block:0",token,"potion",20));assertFalse(r.consume("block:0",token,"potion",21));
    }
    @Test void oldCallbackCannotConsumeNewBrewAndExpiryIsEnforced(){
        var r=new BrewCreditRegistry();r.produce("b:0","p",0);var old=r.peek("b:0","p",0);
        r.produce("b:0","p",1);assertFalse(r.consume("b:0",old.token(),"p",2));
        assertNull(r.peek("b:0","p",1200002));r.produce("b:1","p",3);r.clearBlock("b");assertNull(r.peek("b:1","p",4));
    }
}
