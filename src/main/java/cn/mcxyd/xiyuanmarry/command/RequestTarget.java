package cn.mcxyd.xiyuanmarry.command;
enum RequestTarget {
    PROPOSAL, INVITATION;
    static RequestTarget parse(String[] args) {
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("proposal"))) return PROPOSAL;
        if (args.length == 1 && args[0].equalsIgnoreCase("invitation")) return INVITATION;
        throw new IllegalArgumentException("request target");
    }
}
