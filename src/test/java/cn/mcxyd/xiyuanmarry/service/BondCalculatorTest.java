package cn.mcxyd.xiyuanmarry.service;
import cn.mcxyd.xiyuanmarry.model.*;import org.junit.jupiter.api.Test;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class BondCalculatorTest {
 private List<BondLevel> levels(){return List.of(new BondLevel(1,"倾心",0),new BondLevel(2,"相知",100),new BondLevel(3,"相守",250),new BondLevel(4,"同心",500),new BondLevel(5,"挚爱",1000),new BondLevel(6,"情深",2000),new BondLevel(7,"不渝",3500),new BondLevel(8,"永恒",5500),new BondLevel(9,"共生",8000),new BondLevel(10,"至尊",12000));}
 @Test void levelUsesHighestReachedThreshold(){assertEquals(1,BondCalculator.level(99,levels()));assertEquals(2,BondCalculator.level(100,levels()));assertEquals(10,BondCalculator.level(12000,levels()));}
 @Test void invalidThresholdsRejected(){var x=new ArrayList<>(levels());x.set(2,new BondLevel(3,"相守",100));assertThrows(IllegalArgumentException.class,()->BondCalculator.validate(x));}
}

