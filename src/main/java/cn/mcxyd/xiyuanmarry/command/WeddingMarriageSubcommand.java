package cn.mcxyd.xiyuanmarry.command;

import cn.mcxyd.xiyuanmarry.gui.GuiFactory;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.service.MarriageService;
import cn.mcxyd.xiyuanmarry.service.PlayerDirectory;

public final class WeddingMarriageSubcommand extends AbstractProposalSubcommand {
    public WeddingMarriageSubcommand(MarriageService marriages, PlayerDirectory players,
                                     MessageService messages, GuiFactory gui) {
        super("wedding", "WEDDING", marriages, players, messages, gui);
    }
}
