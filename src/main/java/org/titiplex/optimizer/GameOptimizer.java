package org.titiplex.optimizer;

import org.titiplex.city.Building;
import org.titiplex.city.City;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

public class GameOptimizer {
    private static final Random rnd = new Random(777L);

    public static double penalty(City city) {
        if (city == null) throw new IllegalArgumentException("City is null");
        double penalty = 0;
        for (var b : city.coords_to_building.values().stream().distinct().toList()) {
            if (Building.sameChars(b, new Building(Building.Characteristics.ROAD)) && b.coords().contains(city.start))
                continue;
            boolean ok_road = false;
            boolean ok_rail = false;
            for (var c : b.coords()) {
                for (var n : city.neighbors4(c)) {
                    Building nb = city.coords_to_building.get(n);
                    if (nb != null) {
                        if (b.chars().isNextToRoad && city.coords_to_building.get(n).chars().type == Building.Type.ROAD) {
                            ok_road = true;
                        } else if (!b.chars().isNextToRoad) ok_road = true;
                        if (b.chars().isNextToRail && city.coords_to_building.get(n).chars().type == Building.Type.RAIL) {
                            ok_rail = true;
                        } else if (!b.chars().isNextToRail) ok_rail = true;
                    }
                }
            }
            if (!ok_road) penalty += 5.0;
            if (!ok_rail) penalty += 5.0;
        }
        return penalty;
    }

    public static double score(City city) {

        if (city == null) throw new IllegalArgumentException("City is null");

        List<City.Coordinates> resCells = new ArrayList<>();
        List<City.Coordinates> fireCells = new ArrayList<>();
        List<City.Coordinates> policeCells = new ArrayList<>();
        List<City.Coordinates> healthCells = new ArrayList<>();
        List<City.Coordinates> parkCells = new ArrayList<>();
        List<City.Coordinates> schoolCells = new ArrayList<>();
        List<City.Coordinates> trainCells = new ArrayList<>();
        List<City.Coordinates> factoryCells = new ArrayList<>();

        for (var e : city.coords_to_building.entrySet()) {
            var k = e.getValue().chars().type.getKind();
            var c = e.getKey();
            switch (k) {
                case RES -> resCells.add(c);
                case POLICE -> policeCells.add(c);
                case HEALTH -> healthCells.add(c);
                case PARK -> parkCells.add(c);
                case EDUCATION -> schoolCells.add(c);
                case TRANSIT -> trainCells.add(c);
                case FACTORY -> factoryCells.add(c);
                case FIRE -> fireCells.add(c);
                default -> {
                }
            }
        }

        double score = 0;

        for (var r : resCells) {
            // check if covered by factory pollution radius
            boolean coveredFactory = false;
            for (City.Coordinates s : factoryCells) {
                Building bt = city.coords_to_building.get(s);
                assert bt != null;
                // factories always have square radii
                int radius = bt.chars().radius_x;
                if (City.manhattan(r, s) <= radius) {
                    coveredFactory = true;
                    break;
                }
            }
            if (coveredFactory) score -= 3.0;

            // check if covered by fire
            boolean coveredFire = false;
            for (City.Coordinates s : fireCells) {
                Building bt = city.coords_to_building.get(s);
                assert bt != null;
                int radius_x = bt.chars().radius_x;
                int radius_y = bt.chars().radius_y;
                int dx = Math.abs(r.x() - s.x());
                int dy = Math.abs(r.y() - s.y());
                if (dx <= radius_x && dy <= radius_y) {
                    coveredFire = true;
                    break;
                }
            }
            if (coveredFire) score += 3.0;
            else score -= 2.0;

            // check if covered by police
            boolean coveredPolice = false;
            for (City.Coordinates s : policeCells) {
                Building bt = city.coords_to_building.get(s);
                assert bt != null;
                int radius_x = bt.chars().radius_x;
                int radius_y = bt.chars().radius_y;
                int dx = Math.abs(r.x() - s.x());
                int dy = Math.abs(r.y() - s.y());
                if (dx <= radius_x && dy <= radius_y) {
                    coveredPolice = true;
                    break;
                }
            }
            if (coveredPolice) score += 3.0;
            else score -= 2.0;

            // check if covered by police
            boolean coveredTrain = false;
            for (City.Coordinates s : trainCells) {
                Building bt = city.coords_to_building.get(s);
                assert bt != null;
                int radius_x = bt.chars().radius_x;
                int radius_y = bt.chars().radius_y;
                int dx = Math.abs(r.x() - s.x());
                int dy = Math.abs(r.y() - s.y());
                if (dx <= radius_x && dy <= radius_y) {
                    coveredTrain = true;
                    break;
                }
            }
            if (coveredTrain) score += 1.5;
            else score -= 0.5;

            // check if covered by health
            boolean coveredHealth = false;
            for (City.Coordinates s : healthCells) {
                Building bt = city.coords_to_building.get(s);
                assert bt != null;
                int radius_x = bt.chars().radius_x;
                int radius_y = bt.chars().radius_y;
                int dx = Math.abs(r.x() - s.x());
                int dy = Math.abs(r.y() - s.y());
                if (dx <= radius_x && dy <= radius_y) {
                    coveredHealth = true;
                    break;
                }
            }
            if (coveredHealth) score += 3.0;
            else score -= 2.0;

            // parc / happiness
            if (!parkCells.isEmpty()) {
                int dMin = Integer.MAX_VALUE;
                for (City.Coordinates p : parkCells) {
                    dMin = Math.min(dMin, City.manhattan(r, p));
                }
                // max(0, 3 - 0.5 * d)
                double bonus = 3.0 - 0.5 * dMin;
                if (bonus > 0) score += bonus;
            }

            // schools
            if (!schoolCells.isEmpty()) {
                int dMin = Integer.MAX_VALUE;
                for (City.Coordinates s : schoolCells) {
                    dMin = Math.min(dMin, City.manhattan(r, s));
                }
                if (dMin <= 5) score += 2.0;
            }

            // train station
            if (!trainCells.isEmpty()) {
                int dMin = Integer.MAX_VALUE;
                for (City.Coordinates t : trainCells) {
                    dMin = Math.min(dMin, City.manhattan(r, t));
                }
                double bonus = 2.0 - 0.3 * dMin;
                if (bonus > 0) score += bonus;
            }
        }

        // constraints penalty
        score -= penalty(city);

        return score;
    }

    // random mutations
    public static City randomMutation(City city) {
        City nc = city.deepCopy();
        String action = switch (rnd.nextInt(3)) {
            case 0 -> "add";
            case 1 -> "remove";
            default -> "move";
        };

        List<City.Coordinates> coords = nc.allCoords();
        City.Coordinates c = coords.get(rnd.nextInt(coords.size()));

        if ("remove".equals(action)) {
            if (nc.coords_to_building.containsKey(c)) {
                nc.rmBuilding(c);
            }
        } else if ("add".equals(action)) {
            if (nc.coords_to_building.get(c).chars().type == Building.Type.VOID) {
                Building.Characteristics[] values = Building.Characteristics.values();
                nc.setBuilding(c, new Building(values[rnd.nextInt(values.length)]));
            }
        } else { // move
            Building b = nc.coords_to_building.get(c);
            if (b != null && b.chars().type != Building.Type.VOID) {
                // save old coords to rollback if necessary
                var oldCoords = new HashSet<>(b.coords());

                // del the building
                nc.rmBuilding(c);

                City.Coordinates c2 = coords.get(rnd.nextInt(coords.size()));
                if (nc.coords_to_building.get(c2).chars().type == Building.Type.VOID) {
                    // we recreate a clean building with equal chars
                    Building moved = new Building(b.chars());
                    boolean ok = nc.setBuilding(c2, moved);
                    if (!ok) {
                        // rollback to be safe
                        for (var t : oldCoords) {
                            nc.coords_to_building.put(t, b);
                        }
                        b.coords().clear();
                        b.coords().addAll(oldCoords);
                    }
                }
            }
        }
        return nc;
    }

    // optimisation

    public static City optimizeCity(int iterations, int width, int height) {
        City current = City.randomInitialCity(width, height, rnd);
        current.printCity();
        double currentScore = score(current);

        City best = current.deepCopy();
        double bestScore = currentScore;

        for (int it = 0; it < iterations; it++) {
            City candidate = randomMutation(current);
            double sNew = score(candidate);

            // hill climbing, maybe accept worse possibility
            if (sNew > currentScore || rnd.nextDouble() < 0.01) {
                current = candidate;
                currentScore = sNew;

                if (sNew > bestScore) {
                    best = candidate.deepCopy();
                    bestScore = sNew;
                }
            }
//            best.printCity();
        }

        System.out.println("Best score: " + bestScore);
        return best;
    }
}
