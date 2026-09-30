package cn.mcxyd.xiyuanmarry.command;

import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;
import cn.mcxyd.xiyuanmarry.service.MarriageService;
import cn.mcxyd.xiyuanmarry.service.PlayerDirectory;
import org.bukkit.command.CommandSender;

public final class PartnerSubcommand extends AbstractNoArgumentPlayerSubcommand {
    private final MarriageService marriages;

    public PartnerSubcommand(MarriageService marriages, PlayerDirectory players) {
        super("partner", players);
        this.marriages = marriages;
    }

    @Override
    protected void executeForPlayer(CommandSender sender, PlayerSnapshot actor) {
        marriages.info(sender, actor.id());
    }
}
