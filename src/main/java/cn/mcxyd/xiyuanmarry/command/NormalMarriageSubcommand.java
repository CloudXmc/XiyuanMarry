package cn.mcxyd.xiyuanmarry.command;

import cn.mcxyd.xiyuanmarry.gui.GuiFactory;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.service.MarriageService;
import cn.mcxyd.xiyuanmarry.service.PlayerDirectory;

public final class NormalMarriageSubcommand extends AbstractProposalSubcommand {
    public NormalMarriageSubcommand(MarriageService marriages, PlayerDirectory players,
                                    MessageService messages, GuiFactory gui) {
        super("normal", "NORMAL", marriages, players, messages, gui);
    }
}
