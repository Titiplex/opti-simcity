package org.titiplex.optimizer;

import org.titiplex.city.Building;
import org.titiplex.city.City;

import java.util.*;

public class GameOptimizer {
    static final Random rnd = new Random(777L);

    private static final Map<Building.Type, int[]> TYPE_BOUNDS = Map.of(
            Building.Type.FIRE_STATION, new int[]{1, 4},
            Building.Type.POLICE_STATION, new int[]{1, 4},
            Building.Type.HEALTH_CLINIC, new int[]{1, 4},
            Building.Type.SCHOOL, new int[]{1, 10},
            Building.Type.PARK, new int[]{4, 40},
            Building.Type.RAILWAY_STATION, new int[]{2, 5}
            // etc.  {min, max}
    );

    public static double penalty(City city) {
        if (city == null) throw new IllegalArgumentException("City is null");
        double penalty = 0;
        Set<City.Coordinates> goodRails = railsConnectedToStations(city);
        Set<City.Coordinates> connectedRoads = connectedRoads(city);

        for (var b : city.coords_to_building.values().stream().distinct().toList()) {
            if (b.coords().isEmpty()) continue;
            if (Building.sameChars(b, new Building(Building.Characteristics.ROAD)) && b.coords().contains(city.start))
                continue;
            boolean ok_road = false;
            boolean ok_rail = false;
            for (var c : b.coords()) {
                for (var n : city.neighbors4(c)) {
                    Building nb = city.coords_to_building.get(n);
                    if (nb == null) continue;
                    Building.Type nt = nb.chars().type;
                    if (b.chars().isNextToRoad) {
                        if ((nt == Building.Type.ROAD || nt == Building.Type.CROSSING) &&
                                connectedRoads.contains(n)) {
                            ok_road = true;
                        }
                    } else {
                        ok_road = true;
                    }
                    if (b.chars().isNextToRail) {
                        if ((nt == Building.Type.RAIL || nt == Building.Type.CROSSING) &&
                                goodRails.contains(n)) {
                            ok_rail = true;
                        }
                    } else {
                        ok_rail = true;
                    }
                }
            }
            if (!ok_road) penalty += 5.0;
            if (!ok_rail) penalty += 5.0;
        }
        return penalty;
    }

    private static double buildingCountPenalty(City city) {
        Map<Building.Type, Integer> typeCount = new EnumMap<>(Building.Type.class);
        for (Building b : city.coords_to_building.values().stream().distinct().toList()) {
            typeCount.merge(b.chars().type, 1, Integer::sum);
        }

        double pen = 0.0;

        for (var entry : TYPE_BOUNDS.entrySet()) {
            Building.Type t = entry.getKey();
            int[] bounds = entry.getValue();
            int min = bounds[0];
            int max = bounds[1];
            int n = typeCount.getOrDefault(t, 0);

            if (n < min) {
                pen += (min - n) * 100.0;
            }
            if (n > max) {
                pen += (n - max) * 200.0;
            }
        }

        return pen;
    }

    static Set<City.Coordinates> connectedRoads(City city) {
        var visited = new HashSet<City.Coordinates>();
        var queue = new ArrayDeque<City.Coordinates>();

        City.Coordinates start = city.start;
        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            var c = queue.removeFirst();
            for (var n : city.neighbors4(c)) {
                var b = city.coords_to_building.get(n);
                if (b != null && (b.chars().type == Building.Type.ROAD || b.chars().type == Building.Type.CROSSING) && !visited.contains(n)) {
                    visited.add(n);
                    queue.addLast(n);
                }
            }
        }
        return visited;
    }

    private static Set<City.Coordinates> railsConnectedToStations(City city) {
        List<City.Coordinates> stationCells = new ArrayList<>();
        for (var e : city.coords_to_building.entrySet()) {
            if (e.getValue().chars().type == Building.Type.RAILWAY_STATION) {
                stationCells.add(e.getKey());
            }
        }

        Set<City.Coordinates> visited = new HashSet<>();
        ArrayDeque<City.Coordinates> q = new ArrayDeque<>();

        // BFS from all stations
        for (City.Coordinates s : stationCells) {
            visited.add(s);
            q.add(s);
        }

        while (!q.isEmpty()) {
            City.Coordinates cur = q.removeFirst();
            for (City.Coordinates nb : city.neighbors4(cur)) {
                Building b = city.coords_to_building.get(nb);
                if (b == null) continue;

                // rails and maybe stations
                Building.Type t = b.chars().type;
                if ((t == Building.Type.RAIL
                        || t == Building.Type.RAILWAY_STATION
                        || t == Building.Type.CROSSING)
                        && visited.add(nb)) {
                    q.addLast(nb);
                }
            }
        }
        return visited;
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
            if ((b.chars().type == Building.Type.ROAD || b.chars().type == Building.Type.CROSSING)
                    && !coord.equals(city.start)
                    && !connectedRoads.contains(coord)) {
                penalty += 5_000_000.0;
            }
        }
        return penalty;
    }

    private static double railPenalty(City city) {
        double penalty = 0.0;
        Set<City.Coordinates> railComponent = railsConnectedToStations(city);

        // penalty to orphan rails
        for (var e : city.coords_to_building.entrySet()) {
            City.Coordinates c = e.getKey();
            Building b = e.getValue();
            if (b.chars().type == Building.Type.RAIL && !railComponent.contains(c)) {
                penalty += 500.0;
            }
        }

        // penalty to stations without adjacent rails
        for (var e : city.coords_to_building.entrySet()) {
            City.Coordinates c = e.getKey();
            Building b = e.getValue();
            if (b.chars().type == Building.Type.RAILWAY_STATION) {
                boolean hasRailNeighbour = false;
                for (City.Coordinates nb : city.neighbors4(c)) {
                    Building nbB = city.coords_to_building.get(nb);
                    if (nbB == null) continue;
                    Building.Type t = nbB.chars().type;
                    if (t == Building.Type.RAIL || t == Building.Type.CROSSING) {
                        hasRailNeighbour = true;
                        break;
                    }
                }
                if (!hasRailNeighbour) {
                    penalty += 5_000.0;
                }
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

        fillCells(city, resCells, fireCells, policeCells, healthCells, parkCells, schoolCells, trainCells, factoryCells);

        // list of residential distinct buildings
        Set<Building> resBuildings = new HashSet<>();
        for (City.Coordinates r : resCells) {
            resBuildings.add(city.coords_to_building.get(r));
        }

        // is the building well connected ?
        Map<Building, Boolean> resConnected = new HashMap<>();
        for (Building b : resBuildings) {
            boolean ok = ResidentialOptimizer.isResidentialBuildingWellConnected(b, city, connectedRoads);
            resConnected.put(b, ok);
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
            Building bRes = city.coords_to_building.get(r);
            boolean connectedToEntry = Boolean.TRUE.equals(resConnected.get(bRes));
            if (!connectedToEntry) {
                // penalising only once per cell
                score -= 50.0;
                continue;
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

        // global building cost
        double totalCost = 0.0;
        for (Building b : city.coords_to_building.values().stream().distinct().toList()) {
            totalCost += b.getCost();
        }
        // tune the lambda according to score scale
        score -= 0.1 * totalCost;

        // constraints penalty
        score -= penalty(city);
        score -= roadPenalty(city, connectedRoads);
        score -= railPenalty(city);
        score -= buildingCountPenalty(city);

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

        // 25% of mut are extensions of road
        if (p < 0.2) {
            extendRoadFromNetwork(nc);
            return nc;
        } else if (p < 0.22) {
            addRailStationMutation(nc);
            return nc;
        }

        // 50% improve local worse residency
        if (p < 0.4) {
            ResidentialOptimizer.improveWorstResidence(nc);
            return nc;
        }

        if (p < 0.6) {
            ResidentialOptimizer.removeWorstResidenceIfReallyBad(nc);
            // try to compensate by adding a new RES
            ResidentialOptimizer.tryAddResidentialNearRoad(nc);
            return nc;
        }

        if (p < 0.80) {
            CoverageOptimizer.removeUselessServices(nc);
            return nc;
        }

        // 40% of other mutations : don't touch at roads or res
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

    private static void addRailStationMutation(City city) {
        List<City.Coordinates> roads = new ArrayList<>();
        for (var e : city.coords_to_building.entrySet()) {
            if (e.getValue().chars().type == Building.Type.ROAD) {
                roads.add(e.getKey());
            }
        }
        if (roads.isEmpty()) return;

        City.Coordinates road = roads.get(rnd.nextInt(roads.size()));
        List<City.Coordinates> nbs = city.neighbors4(road);
        Collections.shuffle(nbs, rnd);
        City.Coordinates stationCoord = null;
        for (City.Coordinates nb : nbs) {
            if (city.coords_to_building.get(nb).chars().type == Building.Type.VOID) {
                stationCoord = nb;
                break;
            }
        }
        if (stationCoord == null) return;
        Building station = new Building(Building.Characteristics.SMALL_RAILWAY_STATION);
        if (!city.setBuilding(stationCoord, station)) return;

        List<City.Coordinates> nbs2 = city.neighbors4(stationCoord);
        Collections.shuffle(nbs2, rnd);
        for (City.Coordinates nb : nbs2) {
            if (city.coords_to_building.get(nb).chars() == Building.Characteristics.VOID) {
                city.setBuilding(nb, new Building(Building.Characteristics.RAIL));
                break;
            }
        }
    }

    // optimisation
    public static City optimizeCity(int iterations, int width, int height) {
        City current = City.randomInitialCity(width, height, rnd);
//        current.printCity();
        double currentScore = score(current);

        City best = current.deepCopy();
        double bestScore = currentScore;

        double T0 = 1000.0;   // to adjust
        double alpha = 3.0;   // bigger = quicker cooldown

        for (int it = 0; it < iterations; it++) {
            double t = (double) it / (double) iterations;
            double T = T0 * Math.exp(-alpha * t);
            City candidate = randomMutation(current);
            double sNew = score(candidate);
            double delta = sNew - currentScore;

            boolean accept;
            if (delta >= 0) accept = true;
            else {
                double prob = Math.exp(delta / Math.max(T, 1e-16));
                accept = rnd.nextDouble() < prob;
            }

            if (accept) {
                current = candidate;
                currentScore = sNew;

                if (sNew > bestScore) {
                    best = candidate.deepCopy();
                    bestScore = sNew;
                }
            }
//            best.printCity();
            printProgressBar(it + 1, iterations, 100);
        }

        // post treatment to ensure that the city is connected
        ResidentialOptimizer.connectAllResidencesWithRoads(best);
        System.out.println();
        System.out.println("Best score: " + bestScore);
        debugSummary(best);
        return best;
    }

    private static void printProgressBar(int currentProgress, int totalProgress, int barLength) {
        double percentage = (double) currentProgress / totalProgress;
        int filledLength = (int) (percentage * barLength);
        int emptyLength = barLength - filledLength;

        // Build the progress bar string
        String bar = "[" +
                "=".repeat(Math.max(0, filledLength)) + // Filled part of the bar
                " ".repeat(Math.max(0, emptyLength)) + // Empty part of the bar
                "]";

        // Add percentage
        String percentageText = String.format(" %3d%%", (int) (percentage * 100));

        // Print to console using carriage return to overwrite the line
        System.out.print("\r" + bar + percentageText);
    }

    public static void debugSummary(City city) {
        List<City.Coordinates> resCells = new ArrayList<>();
        List<City.Coordinates> fireCells = new ArrayList<>();
        List<City.Coordinates> policeCells = new ArrayList<>();
        List<City.Coordinates> healthCells = new ArrayList<>();
        List<City.Coordinates> parkCells = new ArrayList<>();
        List<City.Coordinates> schoolCells = new ArrayList<>();
        List<City.Coordinates> trainCells = new ArrayList<>();
        List<City.Coordinates> factoryCells = new ArrayList<>();

        Set<City.Coordinates> connectedRoads = connectedRoads(city);

        fillCells(city, resCells, fireCells, policeCells, healthCells, parkCells, schoolCells, trainCells, factoryCells);

        // distinct res buildings
        Set<Building> resBuildings = new HashSet<>();
        for (City.Coordinates r : resCells) {
            resBuildings.add(city.coords_to_building.get(r));
        }
        Map<Building, Boolean> resConnected = new HashMap<>();
        for (Building b : resBuildings) {
            resConnected.put(b, ResidentialOptimizer.isResidentialBuildingWellConnected(b, city, connectedRoads));
        }

        double sum = 0, min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (City.Coordinates r : resCells) {
            Building br = city.coords_to_building.get(r);
            boolean conn = Boolean.TRUE.equals(resConnected.get(br));
            double loc = ResidentialOptimizer.localScoreForResidence(
                    city, r,
                    fireCells, policeCells, healthCells, parkCells, schoolCells, trainCells, factoryCells,
                    conn
            );
            sum += loc;
            min = Math.min(min, loc);
            max = Math.max(max, loc);
        }
        double avg = resCells.isEmpty() ? 0.0 : sum / resCells.size();

        // total cost
        double totalCost = city.coords_to_building.values()
                .stream().distinct()
                .mapToDouble(Building::getCost)
                .sum();

        // répartition des types
        Map<Building.Type, Integer> typeCount = new EnumMap<>(Building.Type.class);
        for (Building b : city.coords_to_building.values().stream().distinct().toList()) {
            typeCount.merge(b.chars().type, 1, Integer::sum);
        }

        System.out.println("=== DEBUG SUMMARY ===");
        System.out.println("Residential cells: " + resCells.size());
        System.out.println("Residential buildings: " + resBuildings.size());
        System.out.println("Local res score avg/min/max: " + avg + " / " + min + " / " + max);
        System.out.println("Total cost: " + totalCost);
        System.out.println("Building counts:");
        for (var e : typeCount.entrySet()) {
            System.out.println("  " + e.getKey() + " : " + e.getValue());
        }
        System.out.println("=====================");
    }

    static void fillCells(
            City city,
            List<City.Coordinates> resCells,
            List<City.Coordinates> fireCells,
            List<City.Coordinates> policeCells,
            List<City.Coordinates> healthCells,
            List<City.Coordinates> parkCells,
            List<City.Coordinates> schoolCells,
            List<City.Coordinates> trainCells,
            List<City.Coordinates> factoryCells
    ) {
        for (var e : city.coords_to_building.entrySet()) {
            Building.Type t = e.getValue().chars().type;
            City.Coordinates c = e.getKey();

            switch (t) {
                case RESIDENTIAL -> resCells.add(c);
                case FIRE_STATION -> fireCells.add(c);
                case POLICE_STATION -> policeCells.add(c);
                case HEALTH_CLINIC -> healthCells.add(c);
                case PARK -> parkCells.add(c);
                case SCHOOL -> schoolCells.add(c);
                case FACTORY -> factoryCells.add(c);
                case RAILWAY_STATION -> trainCells.add(c);
                default -> {
                    // ROAD, RAIL, CROSSING, VOID, etc.
                }
            }
        }
    }
}