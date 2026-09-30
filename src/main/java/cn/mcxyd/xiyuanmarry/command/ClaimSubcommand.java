package cn.mcxyd.xiyuanmarry.command;

import cn.mcxyd.xiyuanmarry.message.MessageService;
import cn.mcxyd.xiyuanmarry.service.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.UUID;

/** 领取入口只解析参数，查询与发放交给各自的业务服务。 */
public final class ClaimSubcommand implements Subcommand {
    private final ClaimInboxService inbox;
    private final RewardService rewards;
    private final GiftService gifts;
    private final PlayerDirectory players;
    private final MessageService messages;
    public ClaimSubcommand(ClaimInboxService inbox, RewardService rewards, GiftService gifts, PlayerDirectory players, MessageService messages) {
        this.inbox = inbox; this.rewards = rewards; this.gifts = gifts; this.players = players; this.messages = messages;
    }
    public String name() { return "claim"; }
    public List<String> aliases() { return List.of(); }
    public String permission() { return "marry.use"; }
    public boolean playerOnly() { return true; }
    public List<String> complete(CommandSender sender, String[] args) { return List.of(); }
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length > 1) { messages.send(sender, "invalid-argument"); return; }
        if (args.length == 0) {
            var actor = players.capture((Player) sender);
            if (actor == null) { messages.send(sender, "offline"); return; }
            inbox.list(actor);
            return;
        }
        UUID id;
        try { id = UUID.fromString(args[0]); }
        catch (IllegalArgumentException invalid) { messages.send(sender, "invalid-argument"); return; }
        var actor = players.capture((Player) sender);
        // 玩家对象仍在线但身份快照可能在 reload/退出边界失效；不得把空快照送进领取状态机。
        if (actor == null) { messages.send(sender, "offline"); return; }
        rewards.claim(actor, id, handled -> { if (!handled) gifts.claim(actor, id); });
    }
}
