package cn.mcxyd.xiyuanmarry.service;
import cn.mcxyd.xiyuanmarry.model.BondLevel;import java.util.*;
public final class BondCalculator {
 private BondCalculator(){}
 public static int level(long exp,List<BondLevel> levels){return levels.stream().filter(x->exp>=x.required()).max(Comparator.comparingInt(BondLevel::level)).map(BondLevel::level).orElse(1);}
 public static BondLevel current(long exp,List<BondLevel> levels){return levels.stream().filter(x->exp>=x.required()).max(Comparator.comparingInt(BondLevel::level)).orElse(levels.getFirst());}
 public static List<BondLevel> validate(List<BondLevel> levels){if(levels.size()!=10||levels.getFirst().required()!=0)throw new IllegalArgumentException();long last=-1;for(int i=0;i<levels.size();i++){var l=levels.get(i);if(l.level()!=i+1||l.required()<=last)throw new IllegalArgumentException();last=l.required();}return List.copyOf(levels);}
}

