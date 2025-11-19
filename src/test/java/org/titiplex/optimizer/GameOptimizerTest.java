package org.titiplex.optimizer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.titiplex.city.Building;
import org.titiplex.city.City;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GameOptimizerTest {
    static City city;

    @BeforeEach
    public void setUp() {
        // City of 9 plots
        city = new City(3, 3);
        // the clinic should be penalized
        city.setBuilding(new City.Coordinates(2, 2), new Building(Building.Characteristics.BASIC_HEALTH_CLINIC));
        assertNotNull(city);
    }

    @Test
    public void testPenalty() {
        assertEquals(5.0, GameOptimizer.penalty(city));
    }

    @Test
    public void testScore() {
        assertEquals(-5.0, GameOptimizer.score(city));
    }

    @Test
    public void testRandomMutation() {
    }

    @Test
    public void testOptimizeCity() {
    }
}