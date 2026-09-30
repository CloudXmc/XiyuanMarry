package cn.mcxyd.xiyuanmarry.command;

import cn.mcxyd.xiyuanmarry.model.PlayerSnapshot;
import cn.mcxyd.xiyuanmarry.service.PlayerDirectory;

import java.util.List;

/** 只提供唯一玩家参数的候选，前缀大小写过滤由统一补全器处理。 */
final class ProposalPlayerCompleter {
    private final PlayerDirectory players;

    ProposalPlayerCompleter(PlayerDirectory players) {
        this.players = players;
    }

    List<String> complete(String[] args) {
        return args.length == 1
                ? players.all().stream().map(PlayerSnapshot::name).toList()
                : List.of();
    }
}
