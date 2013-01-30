package org.drooms.strategy.runaway;

import org.drooms.api.Strategy;
import org.drooms.impl.util.DroomsTestHelper;

public class RunAwayStrategyTest extends DroomsTestHelper {

    private final Strategy strategy;
    
    public RunAwayStrategyTest() {
        this.strategy = new RunAwayStrategy();
    }
    
    @Override
    public Strategy getStrategy() {
        return this.strategy;
    }

}
