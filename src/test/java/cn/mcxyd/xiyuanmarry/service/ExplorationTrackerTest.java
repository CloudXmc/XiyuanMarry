package cn.mcxyd.xiyuanmarry.service;
import org.junit.jupiter.api.Test;import java.util.UUID;import static org.junit.jupiter.api.Assertions.*;
class ExplorationTrackerTest {
    @Test void centimetresAccumulateWithoutInventingFlightOnToggleOrTeleport(){
        var t=new ExplorationTracker();UUID p=UUID.randomUUID(),w=UUID.randomUUID();
        assertEquals(0,t.sample(p,1000,true,w,"PLAINS",1000,true).metres());
        assertEquals(0,t.sample(p,1060,true,w,"PLAINS",2000,true).metres());
        assertEquals(1,t.sample(p,1110,true,w,"PLAINS",3000,true).metres());
        assertEquals(0,t.sample(p,1110,true,w,"PLAINS",4000,true).metres());
        assertEquals(0,t.sample(p,1510,true,UUID.randomUUID(),"PLAINS",5000,true).metres());
    }
    @Test void aloneOrStaleSamplesAndCounterResetsCannotBankProgress(){
        var t=new ExplorationTracker();UUID p=UUID.randomUUID(),w=UUID.randomUUID();
        t.sample(p,0,true,w,"PLAINS",1000,false);assertEquals(0,t.sample(p,500,true,w,"FOREST",2000,true).metres());
        assertEquals(0,t.sample(p,1000,true,w,"FOREST",7000,true).metres());
        assertEquals(0,t.sample(p,0,true,w,"FOREST",8000,true).metres());t.forget(p);
        assertNull(t.sample(p,200,true,w,"DESERT",9000,true).biome());
    }
    @Test void onlyEnteringAnotherBiomeProducesExploration(){
        var t=new ExplorationTracker();UUID p=UUID.randomUUID(),w=UUID.randomUUID();t.sample(p,0,false,w,"PLAINS",1000,true);
        assertNull(t.sample(p,0,false,w,"PLAINS",2000,true).biome());
        assertEquals("FOREST",t.sample(p,0,false,w,"FOREST",3000,true).biome());
    }
}
