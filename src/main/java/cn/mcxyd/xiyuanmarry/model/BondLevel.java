package cn.mcxyd.xiyuanmarry.model;
import java.util.*;
public record BondLevel(int level,String name,long required){public BondLevel{if(level<1||level>10||required<0||name==null||name.isBlank())throw new IllegalArgumentException();}}

