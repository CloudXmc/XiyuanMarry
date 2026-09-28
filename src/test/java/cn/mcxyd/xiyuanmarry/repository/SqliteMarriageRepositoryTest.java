package cn.mcxyd.xiyuanmarry.repository;

import cn.mcxyd.xiyuanmarry.model.MarriageState;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SqliteMarriageRepositoryTest {
    @Test
    void rejectsSecondRelationshipForEitherPlayer() throws Exception {
        Path file=Files.createTempFile("xiyuan-marry-",".db");
        SqliteMarriageRepository repository=new SqliteMarriageRepository(file);
        UUID one=UUID.randomUUID(),two=UUID.randomUUID(),three=UUID.randomUUID();
        assertTrue(repository.createEngagement(one,two,"WEDDING",1L));
        assertFalse(repository.createEngagement(one,three,"NORMAL",2L));
        assertFalse(repository.createEngagement(three,two,"NORMAL",3L));
        assertEquals(MarriageState.ENGAGED,repository.findByPlayer(two).state());
        repository.close();
        Files.deleteIfExists(file);
    }

    @Test
    void normalAndWeddingStatesAreWrittenExplicitly() throws Exception {
        Path file=Files.createTempFile("xiyuan-marry-",".db");
        SqliteMarriageRepository repository=new SqliteMarriageRepository(file);
        UUID one=UUID.randomUUID(),two=UUID.randomUUID(),three=UUID.randomUUID(),four=UUID.randomUUID();
        assertTrue(repository.createEngagement(one,two,"WEDDING",1L));
        assertTrue(repository.createEngagement(three,four,"NORMAL",2L));
        assertTrue(repository.completeMarriage(three,four,"NORMAL",3L));
        assertEquals(MarriageState.ENGAGED,repository.findByPlayer(one).state());
        assertEquals(MarriageState.MARRIED,repository.findByPlayer(three).state());
        repository.close();
        Files.deleteIfExists(file);
    }

    @Test
    void bondCannotBeReducedByInvalidInput() throws Exception {
        Path file=Files.createTempFile("xiyuan-marry-",".db");
        SqliteMarriageRepository repository=new SqliteMarriageRepository(file);
        UUID one=UUID.randomUUID(),two=UUID.randomUUID();
        repository.createEngagement(one,two,"NORMAL",1L);
        assertTrue(repository.completeMarriage(one,two,"NORMAL",2L));
        assertFalse(repository.addBond(one,0));
        assertFalse(repository.addBond(one,-5));
        assertTrue(repository.addBond(one,10));
        assertEquals(10,repository.findByPlayer(one).bond());
        repository.close();
        Files.deleteIfExists(file);
    }

    @Test
    void engagementExpiresAndDivorceCoolingRemovesOnlyWhenDue() throws Exception {
        Path file=Files.createTempFile("xiyuan-marry-",".db");
        SqliteMarriageRepository repository=new SqliteMarriageRepository(file);
        UUID one=UUID.randomUUID(),two=UUID.randomUUID();
        repository.createEngagement(one,two,"WEDDING",10L);
        assertEquals(0,repository.expireEngagements(9L));
        assertEquals(1,repository.expireEngagements(10L));
        repository.createEngagement(one,two,"WEDDING",11L);
        assertTrue(repository.completeMarriage(one,two,"WEDDING",12L));
        assertTrue(repository.requestDivorce(one,20L));
        assertEquals(0,repository.completeDueDivorces(19L));
        assertEquals(1,repository.completeDueDivorces(20L));
        repository.close(); Files.deleteIfExists(file);
    }
}

