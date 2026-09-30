package cn.mcxyd.xiyuanmarry.command;

import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;
import cn.mcxyd.xiyuanmarry.service.MarriageService;
import cn.mcxyd.xiyuanmarry.service.PlayerDirectory;
import org.bukkit.command.CommandSender;

public final class CancelSubcommand extends AbstractNoArgumentPlayerSubcommand {
    private final MarriageService marriages;

    public CancelSubcommand(MarriageService marriages, PlayerDirectory players) {
        super("cancel", players);
        this.marriages = marriages;
    }

    @Override
    protected void executeForPlayer(CommandSender sender, PlayerSnapshot actor) {
        marriages.cancel(actor);
    }
}
