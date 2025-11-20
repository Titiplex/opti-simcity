package org.titiplex.optimizer;

import org.titiplex.city.Building;
import org.titiplex.city.City;

import java.util.*;

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

    private static Set<City.Coordinates> connectedRoads(City city) {
        var visited = new HashSet<City.Coordinates>();
        var queue = new ArrayDeque<City.Coordinates>();

        City.Coordinates start = city.start;
        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            var c = queue.removeFirst();
            for (var n : city.neighbors4(c)) {
                var b = city.coords_to_building.get(n);
                if (b != null && b.chars().type == Building.Type.ROAD && !visited.contains(n)) {
                    visited.add(n);
                    queue.addLast(n);
                }
            }
        }
        return visited;
    }

    private static void connectAllResidencesWithRoads(City city) {
        boolean changed = true;

        while (changed) {
            changed = false;
            Set<City.Coordinates> connected = connectedRoads(city);
            List<City.Coordinates> resCells = new ArrayList<>();
            for (var e : city.coords_to_building.entrySet()) {
                if (e.getValue().chars().type == Building.Type.RESIDENTIAL) {
                    resCells.add(e.getKey());
                }
            }

            for (City.Coordinates r : resCells) {
                // already adjacent to a connected road ?
                boolean ok = false;
                for (City.Coordinates n : city.neighbors4(r)) {
                    if (connected.contains(n)) {
                        ok = true;
                        break;
                    }
                }
                if (ok) continue;

                // BFS from residence to connected road
                Map<City.Coordinates, City.Coordinates> parent = new HashMap<>();
                ArrayDeque<City.Coordinates> q = new ArrayDeque<>();
                q.add(r);
                parent.put(r, null);

                City.Coordinates target = null;

                while (!q.isEmpty() && target == null) {
                    City.Coordinates cur = q.removeFirst();
                    for (City.Coordinates nb : city.neighbors4(cur)) {
                        if (parent.containsKey(nb)) continue;
                        Building b = city.coords_to_building.get(nb);
                        if (b == null) continue;

                        Building.Type t = b.chars().type;

                        // always pass on road or void
                        if (t != Building.Type.VOID && t != Building.Type.ROAD) continue;

                        parent.put(nb, cur);

                        if (connected.contains(nb)) {
                            target = nb;
                            break;
                        }
                        q.addLast(nb);
                    }
                }

                if (target == null) {
                    // no road found, we forsake that residency
                    continue;
                }

                // go from r to target and transform void into road
                City.Coordinates cur = parent.get(target);
                while (cur != null && !cur.equals(r)) {
                    Building b = city.coords_to_building.get(cur);
                    if (b.chars().type == Building.Type.VOID) {
                        city.setBuilding(cur, new Building(Building.Characteristics.ROAD));
                        changed = true;
                    }
                    cur = parent.get(cur);
                }
            }
        }
    }

    /**
     * Penalizes roads that are not connected to the start point.
     *
     * @param city the city to evaluate.
     * @return a penalty score (malus)
     */
    private static double roadPenalty(City city, Set<City.Coordinates> connectedRoads) {
        double penalty = 0.0;
        for (var entry : city.coords_to_building.entrySet()) {
            var coord = entry.getKey();
            var b = entry.getValue();
            if (b.chars().type == Building.Type.ROAD
                    && !coord.equals(city.start)
                    && !connectedRoads.contains(coord)) {
                penalty += 5_000_000.0;
            }
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

        // roads
        Set<City.Coordinates> connectedRoads = connectedRoads(city);

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

        // hard constraint on the number of residencies
        int targetRes = (city.getWidth() * city.getHeight()) / 8;
        int currentRes = resCells.size();

        if (currentRes == 0) {
            // city without inhabitants -> impossible
            return -1e9;
        }

        // a little flexible
        if (currentRes < targetRes) {
            score -= (targetRes - currentRes) * 10.0;
        }
//        else if (currentRes > targetRes) {
//            score -= (currentRes - targetRes) * 20.0;
//        }

        for (var r : resCells) {

            // check if residency is connected to the start point through road network
            boolean connectedToEntry = false;
            for (City.Coordinates n : city.neighbors4(r)) {
                if (connectedRoads.contains(n)) {
                    connectedToEntry = true;
                    break;
                }
            }
            if (!connectedToEntry) {
                score -= 50.0;
                continue; // not counting bonus, just penalties, which is problematic
            }

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
        score -= roadPenalty(city, connectedRoads);

        return score;
    }

    private static void extendRoadFromNetwork(City city) {
        // fetch already connected roads
        Set<City.Coordinates> connected = connectedRoads(city);
        if (connected.isEmpty()) return;

        // choose one connected road as starting point (random)
        List<City.Coordinates> roadList = new ArrayList<>(connected);
        City.Coordinates base = roadList.get(rnd.nextInt(roadList.size()));

        // try to add a road on a void neighbor
        List<City.Coordinates> neighbors = city.neighbors4(base);
        Collections.shuffle(neighbors, rnd);
        for (City.Coordinates n : neighbors) {
            Building b = city.coords_to_building.get(n);
            if (b != null && b.chars().type == Building.Type.VOID) {
                city.setBuilding(n, new Building(Building.Characteristics.ROAD));
                break;
            }
        }
    }

    // random mutations
    public static City randomMutation(City city) {
        City nc = city.deepCopy();

        double p = rnd.nextDouble();

        // 40% of mut are extensions of road
        if (p < 0.4) {
            extendRoadFromNetwork(nc);
            return nc;
        }

        // 60% of other mutations : don't touch at roads or res
        String action = switch (rnd.nextInt(3)) {
            case 0 -> "add";
            case 1 -> "remove";
            default -> "move";
        };

        List<City.Coordinates> coords = nc.allCoords();
        City.Coordinates c = coords.get(rnd.nextInt(coords.size()));
        Building b = nc.coords_to_building.get(c);

        if ("remove".equals(action)) {
            // don't remove res of entry route
            if (b != null
                    && b.chars().type != Building.Type.RESIDENTIAL
                    && !(b.chars().type == Building.Type.ROAD && c.equals(nc.start))) {
                nc.rmBuilding(c);
            }
        } else if ("add".equals(action)) {
            if (b.chars().type == Building.Type.VOID) {
                // no random res or road
                Building.Characteristics[] values = Building.Characteristics.values();
                Building.Characteristics chosen;
                do {
                    chosen = values[rnd.nextInt(values.length)];
                } while (chosen.type == Building.Type.RESIDENTIAL
                        || chosen == Building.Characteristics.ROAD);

                nc.setBuilding(c, new Building(chosen));
            }
        } else { // move
            if (b != null
                    && b.chars().type != Building.Type.VOID
                    && b.chars().type != Building.Type.RESIDENTIAL
                    && !(b.chars().type == Building.Type.ROAD && c.equals(nc.start))) {

                var oldCoords = new HashSet<>(b.coords());
                nc.rmBuilding(c);

                City.Coordinates c2 = coords.get(rnd.nextInt(coords.size()));
                if (nc.coords_to_building.get(c2).chars().type == Building.Type.VOID) {
                    Building moved = new Building(b.chars());
                    boolean ok = nc.setBuilding(c2, moved);
                    if (!ok) {
                        // rollback
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
//        current.printCity();
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

        // post treatment to ensure that the city is connected
        connectAllResidencesWithRoads(best);
        System.out.println("Best score: " + bestScore);
        return best;
    }
}
