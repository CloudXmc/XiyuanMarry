package cn.mcxyd.xiyuanmarry.command;

import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;
import cn.mcxyd.xiyuanmarry.service.MarriageService;
import cn.mcxyd.xiyuanmarry.service.PlayerDirectory;
import org.bukkit.command.CommandSender;

public final class WithdrawSubcommand extends AbstractNoArgumentPlayerSubcommand {
    private final MarriageService marriages;

    public WithdrawSubcommand(MarriageService marriages, PlayerDirectory players) {
        super("withdraw", players);
        this.marriages = marriages;
    }

    @Override
    protected void executeForPlayer(CommandSender sender, PlayerSnapshot actor) {
        marriages.divorce(actor, true);
    }
}
