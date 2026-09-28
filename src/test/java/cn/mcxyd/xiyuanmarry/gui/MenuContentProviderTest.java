package cn.mcxyd.xiyuanmarry.gui;
import cn.mcxyd.xiyuanmarry.config.ConfigurationManager;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.service.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class MenuContentProviderTest {
    MenuContentProvider provider(){
        var config=mock(ConfigurationManager.class);
        when(config.messages()).thenReturn(YamlConfiguration.loadConfiguration(Path.of("src/main/resources/messages.yml").toFile()));
        return new MenuContentProvider(config,mock(MarriageService.class),new MessageService(config));
    }
    @Test void claimableTicketsKeepTheirExactIdsAndReviewIsReadOnly(){
        UUID reward=UUID.randomUUID(),gift=UUID.randomUUID(),review=UUID.randomUUID();
        var entries=provider().inbox(List.of(InboxMessage.of("reward-pending","count",1),
                InboxMessage.of("reward-id","id",reward,"days",7,"experience",10),
                InboxMessage.of("gift-id","id",gift,"sender","Alice"),
                InboxMessage.of("reward-review-id","id",review),InboxMessage.of("delivery-review")));
        assertEquals(4,entries.size());assertEquals(reward.toString(),entries.get(0).value());
        assertEquals(gift.toString(),entries.get(1).value());
        assertTrue(entries.get(2).value().isEmpty());assertTrue(entries.get(3).value().isEmpty());
        entries.forEach(entry->assertFalse(entry.tokens().get("name").toString().isBlank()));
        assertTrue(entries.get(1).tokens().get("description").toString().contains("Alice"));
    }
    @Test void emptyCategoriesDoNotCreateClickablePlaceholders(){
        assertTrue(provider().inbox(List.of(InboxMessage.of("reward-none"),InboxMessage.of("gift-none"))).isEmpty());
    }
    @Test void searchingCanFindPlayerBeyondFirstThirtySlots(){
        var entries=new ArrayList<MenuContentProvider.Entry>();
        for(int i=0;i<45;i++)entries.add(new MenuContentProvider.Entry("id-"+i,Map.of("name","Player"+i,"description","online")));
        var selected=MenuContentProvider.filter(entries,"pLaYeR44");
        assertEquals(1,selected.size());assertEquals("id-44",selected.getFirst().value());
        assertEquals(45,MenuContentProvider.filter(entries,"").size());
    }
    @Test void invitationIdsAndNamesAreSearchable(){
        var entry=new MenuContentProvider.Entry("wedding-123",Map.of("name","Alice ♥ Bob","description","婚礼请帖"));
        assertEquals(List.of(entry),MenuContentProvider.filter(List.of(entry),"wedding-123"));
        assertEquals(List.of(entry),MenuContentProvider.filter(List.of(entry),"bob"));
        assertTrue(MenuContentProvider.filter(List.of(entry),"unknown").isEmpty());
    }
}
