package cn.mcxyd.xiyuanmarry.service;
public final class RuleViolation extends RuntimeException{private final String key;public RuleViolation(String key){super(key);this.key=key;}public String key(){return key;}public static void require(boolean condition,String key){if(!condition)throw new RuleViolation(key);}}

