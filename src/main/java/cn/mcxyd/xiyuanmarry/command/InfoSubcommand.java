package cn.mcxyd.xiyuanmarry.command;

import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;
import cn.mcxyd.xiyuanmarry.service.MarriageService;
import cn.mcxyd.xiyuanmarry.service.PlayerDirectory;
import org.bukkit.command.CommandSender;

public final class InfoSubcommand extends AbstractNoArgumentPlayerSubcommand {
    private final MarriageService marriages;

    public InfoSubcommand(MarriageService marriages, PlayerDirectory players) {
        super("info", players);
        this.marriages = marriages;
    }

    @Override
    protected void executeForPlayer(CommandSender sender, PlayerSnapshot actor) {
        marriages.info(sender, actor.id());
    }
}
