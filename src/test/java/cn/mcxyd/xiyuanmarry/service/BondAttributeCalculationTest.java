package cn.mcxyd.xiyuanmarry.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BondAttributeCalculationTest {
    @Test void firstLevelAndDisabledFeatureHaveNoBonus() {
        assertEquals(new BondAttributeService.Bonus(0,0,0), BondAttributeService.calculate(1,true,1,.25,.01));
        assertEquals(new BondAttributeService.Bonus(0,0,0), BondAttributeService.calculate(10,false,1,.25,.01));
    }
    @Test void levelsAreCappedAtTheExistingTenLevelLimit() {
        assertEquals(new BondAttributeService.Bonus(9,2.25,.09), BondAttributeService.calculate(99,true,1,.25,.01));
    }
    @Test void invalidRatesCannotCreateInvalidModifiers() {
        assertThrows(IllegalArgumentException.class, () -> BondAttributeService.calculate(3,true,Double.NaN,.25,.01));
        assertThrows(IllegalArgumentException.class, () -> BondAttributeService.calculate(3,true,1,-.25,.01));
        assertThrows(IllegalArgumentException.class, () -> BondAttributeService.calculate(3,true,1,.25,Double.POSITIVE_INFINITY));
    }
}
