package cn.mcxyd.xiyuanmarry.model;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class CoupleTaskStateTest{@Test void progressCapsAndCompletes(){var s=new CoupleTaskState("c",1,"PLACE_BLOCK",0,10,false,0).add(12);assertEquals(10,s.progress());assertTrue(s.completed());}@Test void progressCannotBeNegative(){assertThrows(IllegalArgumentException.class,()->new CoupleTaskState("c",1,"PLACE_BLOCK",-1,10,false,0));}}

