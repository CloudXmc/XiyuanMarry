package cn.mcxyd.xiyuanmarry.command;
public final class AdminConfirmation {
    private AdminConfirmation(){}
    public static boolean required(String name){return name.equals("force")||name.equals("divorce")||name.equals("clear");}
    public static boolean accepted(String name,String[] arguments){
        if(!required(name))return true;int length=name.equals("force")?3:2;
        return arguments.length==length&&arguments[length-1].equalsIgnoreCase("confirm");
    }
}
