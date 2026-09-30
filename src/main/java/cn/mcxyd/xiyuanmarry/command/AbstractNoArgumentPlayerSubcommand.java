package cn.mcxyd.xiyuanmarry.command;

import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;
import cn.mcxyd.xiyuanmarry.service.PlayerDirectory;
import cn.mcxyd.xiyuanmarry.service.RuleViolation;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** 先拒绝错误参数，再在玩家上下文采集快照；提示交给主命令统一发送。 */
abstract class AbstractNoArgumentPlayerSubcommand implements Subcommand {
    private final String name;
    private final PlayerDirectory players;

    AbstractNoArgumentPlayerSubcommand(String name, PlayerDirectory players) {
        this.name = name;
        this.players = players;
    }

    @Override public final String name() { return name; }
    @Override public final List<String> aliases() { return List.of(); }
    @Override public final String permission() { return "marry.use"; }
    @Override public final boolean playerOnly() { return true; }
    @Override public final List<String> complete(CommandSender sender, String[] args) { return List.of(); }

    @Override
    public final void execute(CommandSender sender, String label, String[] args) {
        RuleViolation.require(args.length == 0, "invalid-argument");
        PlayerSnapshot actor = players.capture((Player) sender);
        RuleViolation.require(actor != null, "database-not-ready");
        executeForPlayer(sender, actor);
    }

    protected abstract void executeForPlayer(CommandSender sender, PlayerSnapshot actor);
}
