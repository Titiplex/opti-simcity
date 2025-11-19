package org.titiplex.city;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class CityTest {

    static City city;

    @BeforeEach
    public void setUp() {
        city = new City(3, 3);
    }

    @Test
    public void start() {
        assertEquals(new City.Coordinates(0, 1), city.start);
        assertNotEquals(new City.Coordinates(0, 0), city.start);
    }

    @Test
    public void inside() {
        assertFalse(city.inside(new City.Coordinates(0, 1)));
        assertTrue(city.inside(new City.Coordinates(1, 1)));
        assertFalse(city.inside(new City.Coordinates(3, 1)));
        assertFalse(city.inside(new City.Coordinates(1, 3)));
    }

    @Test
    public void setBuilding() {
        var outOfBounds = new City.Coordinates(3, 3);
        assertFalse(city.setBuilding(outOfBounds, new Building(Building.Characteristics.BASIC_HEALTH_CLINIC)));
        assertTrue(city.setBuilding(new City.Coordinates(1, 1), new Building(Building.Characteristics.BASIC_HEALTH_CLINIC)));
        assertFalse(city.setBuilding(new City.Coordinates(0, 1), new Building(Building.Characteristics.BASIC_HEALTH_CLINIC)));
    }

    @Test
    public void hasBuilding() {
        city.setBuilding(new City.Coordinates(1, 1), new Building(Building.Characteristics.BASIC_HEALTH_CLINIC));
        assertTrue(city.hasBuilding(new City.Coordinates(1, 1)));
    }

    @Test
    public void getBuilding() {
        assertEquals(Building.Characteristics.ROAD, city.coords_to_building.get(new City.Coordinates(0, 1)).chars());
    }

    @Test
    public void neighbors4() {
        List<City.Coordinates> res = city.neighbors4(new City.Coordinates(1, 1));
        assertEquals(4, res.size());
        assertTrue(res.contains(new City.Coordinates(1, 0)));
        assertTrue(res.contains(new City.Coordinates(0, 1)));
        assertTrue(res.contains(new City.Coordinates(2, 1)));
        assertTrue(res.contains(new City.Coordinates(1, 2)));
    }

    @Test
    public void deepCopy() {
        City c = city.deepCopy();
        assertEquals(city, c);
        assertNotSame(city, c);
    }

    @Test
    public void randomInitialCity() {
        var rCity = City.randomInitialCity(3, 3, new Random(0L));
        assertTrue(rCity.allCoords().stream().anyMatch(c -> c != rCity.start && rCity.hasBuilding(c)));
    }

    @Test
    public void allCoords() {
        List<City.Coordinates> coords = city.allCoords();

        assertEquals(9, coords.size());
        assertTrue(coords.contains(new City.Coordinates(0, 0)));
        assertTrue(coords.contains(new City.Coordinates(0, 1)));
        assertTrue(coords.contains(new City.Coordinates(0, 2)));
        assertTrue(coords.contains(new City.Coordinates(1, 0)));
        assertTrue(coords.contains(new City.Coordinates(1, 1)));
        assertTrue(coords.contains(new City.Coordinates(1, 2)));
        assertTrue(coords.contains(new City.Coordinates(2, 0)));
        assertTrue(coords.contains(new City.Coordinates(2, 1)));
        assertTrue(coords.contains(new City.Coordinates(2, 2)));
    }
}