package cn.mcxyd.xiyuanmarry.service;
public final class RuleViolation extends RuntimeException {
    private final String key;
    private final Object[] values;
    public RuleViolation(String key,Object...values){super(key);this.key=key;this.values=values.clone();}
    public String key(){return key;}
    public Object[] values(){return values.clone();}
    public static void require(boolean condition,String key){if(!condition)throw new RuleViolation(key);}
}

