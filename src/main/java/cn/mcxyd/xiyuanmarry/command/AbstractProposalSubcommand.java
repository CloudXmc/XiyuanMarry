package cn.mcxyd.xiyuanmarry.command;

import cn.mcxyd.xiyuanmarry.gui.GuiFactory;
import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;
import cn.mcxyd.xiyuanmarry.service.MarriageService;
import cn.mcxyd.xiyuanmarry.service.PlayerDirectory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** 三个求婚入口共用参数、快照和目标校验，避免命令分支产生不同安全边界。 */
abstract class AbstractProposalSubcommand implements Subcommand {
    private final String name;
    private final String mode;
    private final MarriageService marriages;
    private final PlayerDirectory players;
    private final MessageService messages;
    private final GuiFactory gui;
    private final ProposalPlayerCompleter completer;

    AbstractProposalSubcommand(String name, String mode, MarriageService marriages,
                                PlayerDirectory players, MessageService messages, GuiFactory gui) {
        this.name = name;
        this.mode = mode;
        this.marriages = marriages;
        this.players = players;
        this.messages = messages;
        this.gui = gui;
        this.completer = new ProposalPlayerCompleter(players);
    }

    @Override public final String name() { return name; }
    @Override public final List<String> aliases() { return List.of(); }
    @Override public final String permission() { return "marry.use"; }
    @Override public final boolean playerOnly() { return true; }

    @Override
    public final void execute(CommandSender sender, String label, String[] args) {
        if (args.length > 1) {
            messages.send(sender, "invalid-argument");
            return;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            gui.open(player, "propose", mode, 0);
            return;
        }
        PlayerSnapshot actor = players.capture(player);
        if (actor == null) {
            messages.send(sender, "offline");
            return;
        }
        PlayerSnapshot target = players.name(args[0]);
        if (target == null) {
            messages.send(sender, "offline");
            return;
        }
        marriages.propose(actor, target, mode);
    }

    @Override
    public final List<String> complete(CommandSender sender, String[] args) {
        return completer.complete(args);
    }
}
