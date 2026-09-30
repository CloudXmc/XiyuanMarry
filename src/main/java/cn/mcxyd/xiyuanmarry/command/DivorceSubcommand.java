package cn.mcxyd.xiyuanmarry.command;

import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;
import cn.mcxyd.xiyuanmarry.service.MarriageService;
import cn.mcxyd.xiyuanmarry.service.PlayerDirectory;
import org.bukkit.command.CommandSender;

public final class DivorceSubcommand extends AbstractNoArgumentPlayerSubcommand {
    private final MarriageService marriages;

    public DivorceSubcommand(MarriageService marriages, PlayerDirectory players) {
        super("divorce", players);
        this.marriages = marriages;
    }

    @Override
    protected void executeForPlayer(CommandSender sender, PlayerSnapshot actor) {
        marriages.divorce(actor, false);
    }
}
