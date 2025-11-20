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

            // distinct residential buildings
            List<Building> resBuildings = city.coords_to_building.values().stream()
                    .filter(b -> b.chars().type == Building.Type.RESIDENTIAL)
                    .distinct()
                    .toList();

            for (Building bRes : resBuildings) {
                if (isResidentialBuildingWellConnected(bRes, city, connected)) {
                    continue;
                }

                // we take an external point for the BFS
                City.Coordinates startFrom = bRes.coords().iterator().next();

                Map<City.Coordinates, City.Coordinates> parent = new HashMap<>();
                ArrayDeque<City.Coordinates> q = new ArrayDeque<>();
                q.add(startFrom);
                parent.put(startFrom, null);

                City.Coordinates target = null;

                while (!q.isEmpty() && target == null) {
                    City.Coordinates cur = q.removeFirst();
                    for (City.Coordinates nb : city.neighbors4(cur)) {
                        if (parent.containsKey(nb)) continue;
                        Building b = city.coords_to_building.get(nb);
                        if (b == null) continue;

                        Building.Type t = b.chars().type;
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
                    // no good path, we forsake the building
                    continue;
                }

                // go uproad and transform void into road cells
                City.Coordinates cur = parent.get(target);
                while (cur != null && !cur.equals(startFrom)) {
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

    private static boolean isResidentialBuildingWellConnected(
            Building b,
            City city,
            Set<City.Coordinates> connectedRoads
    ) {
        if (b.chars().type != Building.Type.RESIDENTIAL) return true; // on ne pénalise que les RES

        if (b.coords().isEmpty()) return false;

        int xmin = Integer.MAX_VALUE, xmax = Integer.MIN_VALUE;
        int ymin = Integer.MAX_VALUE, ymax = Integer.MIN_VALUE;

        for (City.Coordinates c : b.coords()) {
            xmin = Math.min(xmin, c.x());
            xmax = Math.max(xmax, c.x());
            ymin = Math.min(ymin, c.y());
            ymax = Math.max(ymax, c.y());
        }

        int north = 0, south = 0, west = 0, east = 0;

        for (City.Coordinates c : b.coords()) {
            boolean isNorth = (c.y() == ymin);
            boolean isSouth = (c.y() == ymax);
            boolean isWest = (c.x() == xmin);
            boolean isEast = (c.x() == xmax);

            for (City.Coordinates n : city.neighbors4(c)) {
                if (!connectedRoads.contains(n)) continue;

                // same cell can have multiple side
                if (isNorth) north++;
                if (isSouth) south++;
                if (isWest) west++;
                if (isEast) east++;
            }
        }

        // condition : at least 2 cells are adjacent to a road
        return north >= 2 || south >= 2 || west >= 2 || east >= 2;
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

    /**
     * Local score for a residential building, to guide mutations
     *
     */
    private static double localScoreForResidence(
            City city,
            City.Coordinates r,
            List<City.Coordinates> fireCells,
            List<City.Coordinates> policeCells,
            List<City.Coordinates> healthCells,
            List<City.Coordinates> parkCells,
            List<City.Coordinates> schoolCells,
            List<City.Coordinates> trainCells,
            List<City.Coordinates> factoryCells,
            boolean connectedToEntry
    ) {
        double s = 0.0;

        if (!connectedToEntry) {
            s -= 50.0;
            return s;
        }

        boolean coveredFactory = false;
        for (City.Coordinates sF : factoryCells) {
            Building bt = city.coords_to_building.get(sF);
            int radius = bt.chars().radius_x;
            if (City.manhattan(r, sF) <= radius) {
                coveredFactory = true;
                break;
            }
        }
        if (coveredFactory) s -= 3.0;

        // fire
        boolean coveredFire = false;
        for (City.Coordinates sF : fireCells) {
            Building bt = city.coords_to_building.get(sF);
            int rx = bt.chars().radius_x, ry = bt.chars().radius_y;
            int dx = Math.abs(r.x() - sF.x()), dy = Math.abs(r.y() - sF.y());
            if (dx <= rx && dy <= ry) {
                coveredFire = true;
                break;
            }
        }
        s += coveredFire ? 3.0 : -2.0;

        // police
        boolean coveredPolice = false;
        for (City.Coordinates sP : policeCells) {
            Building bt = city.coords_to_building.get(sP);
            int rx = bt.chars().radius_x, ry = bt.chars().radius_y;
            int dx = Math.abs(r.x() - sP.x()), dy = Math.abs(r.y() - sP.y());
            if (dx <= rx && dy <= ry) {
                coveredPolice = true;
                break;
            }
        }
        s += coveredPolice ? 3.0 : -2.0;

        // train
        boolean coveredTrain = false;
        for (City.Coordinates sT : trainCells) {
            Building bt = city.coords_to_building.get(sT);
            int rx = bt.chars().radius_x, ry = bt.chars().radius_y;
            int dx = Math.abs(r.x() - sT.x()), dy = Math.abs(r.y() - sT.y());
            if (dx <= rx && dy <= ry) {
                coveredTrain = true;
                break;
            }
        }
        s += coveredTrain ? 1.5 : -0.5;

        // health
        boolean coveredHealth = false;
        for (City.Coordinates sH : healthCells) {
            Building bt = city.coords_to_building.get(sH);
            int rx = bt.chars().radius_x, ry = bt.chars().radius_y;
            int dx = Math.abs(r.x() - sH.x()), dy = Math.abs(r.y() - sH.y());
            if (dx <= rx && dy <= ry) {
                coveredHealth = true;
                break;
            }
        }
        s += coveredHealth ? 3.0 : -2.0;

        // parc
        if (!parkCells.isEmpty()) {
            int dMin = Integer.MAX_VALUE;
            for (City.Coordinates p : parkCells) {
                dMin = Math.min(dMin, City.manhattan(r, p));
            }
            double bonus = 3.0 - 0.5 * dMin;
            if (bonus > 0) s += bonus;
        }

        // écoles
        if (!schoolCells.isEmpty()) {
            int dMin = Integer.MAX_VALUE;
            for (City.Coordinates sS : schoolCells) {
                dMin = Math.min(dMin, City.manhattan(r, sS));
            }
            if (dMin <= 5) s += 2.0;
        }

        // train distance
        if (!trainCells.isEmpty()) {
            int dMin = Integer.MAX_VALUE;
            for (City.Coordinates t : trainCells) {
                dMin = Math.min(dMin, City.manhattan(r, t));
            }
            double bonus = 2.0 - 0.3 * dMin;
            if (bonus > 0) s += bonus;
        }

        return s;
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

        // list of residential distinct buildings
        Set<Building> resBuildings = new HashSet<>();
        for (City.Coordinates r : resCells) {
            resBuildings.add(city.coords_to_building.get(r));
        }

        // is the building well connected ?
        Map<Building, Boolean> resConnected = new HashMap<>();
        for (Building b : resBuildings) {
            boolean ok = isResidentialBuildingWellConnected(b, city, connectedRoads);
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

        return score;
    }

    private static City.Coordinates findWorstResidence(City city) {
        List<City.Coordinates> resCells = new ArrayList<>();
        List<City.Coordinates> fireCells = new ArrayList<>();
        List<City.Coordinates> policeCells = new ArrayList<>();
        List<City.Coordinates> healthCells = new ArrayList<>();
        List<City.Coordinates> parkCells = new ArrayList<>();
        List<City.Coordinates> schoolCells = new ArrayList<>();
        List<City.Coordinates> trainCells = new ArrayList<>();
        List<City.Coordinates> factoryCells = new ArrayList<>();

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

        if (resCells.isEmpty()) return null;

        // list of residential distinct buildings
        Set<Building> resBuildings = new HashSet<>();
        for (City.Coordinates r : resCells) {
            resBuildings.add(city.coords_to_building.get(r));
        }

        Map<Building, Boolean> resConnected = new HashMap<>();
        for (Building b : resBuildings) {
            boolean ok = isResidentialBuildingWellConnected(b, city, connectedRoads);
            resConnected.put(b, ok);
        }

        double worstScore = Double.POSITIVE_INFINITY;
        City.Coordinates worst = null;

        for (City.Coordinates r : resCells) {
            Building bRes = city.coords_to_building.get(r);
            boolean connectedToEntry = Boolean.TRUE.equals(resConnected.get(bRes));
            double sLoc = localScoreForResidence(
                    city,
                    r,
                    fireCells,
                    policeCells,
                    healthCells,
                    parkCells,
                    schoolCells,
                    trainCells,
                    factoryCells,
                    connectedToEntry
            );
            if (sLoc < worstScore) {
                worstScore = sLoc;
                worst = r;
            }
        }

        return worst;
    }

    private static void improveWorstResidence(City city) {
        City.Coordinates r = findWorstResidence(city);
        if (r == null) return;

        // candidats : voisinage de rayon 1 et 2 autour de la pire résidence
        List<City.Coordinates> candidates = new ArrayList<>(city.neighbors4(r));
        for (City.Coordinates c1 : city.neighbors4(r)) {
            candidates.addAll(city.neighbors4(c1));
        }

        // dédoublonnage
        LinkedHashSet<City.Coordinates> uniq = new LinkedHashSet<>(candidates);
        List<City.Coordinates> list = new ArrayList<>(uniq);
        Collections.shuffle(list, rnd);

        // types "utiles" localement pour améliorer la vie des habitants
        Building.Characteristics[] useful = new Building.Characteristics[]{
                Building.Characteristics.SMALL_FOUNTAIN_PARK,
                Building.Characteristics.SMALL_HEALTH_CLINIC,
                Building.Characteristics.SMALL_POLICE_STATION,
                Building.Characteristics.SMALL_FIRE_STATION,
                Building.Characteristics.NURSERY_SCHOOL,
                Building.Characteristics.SMALL_RAILWAY_STATION
        };

        for (City.Coordinates c : list) {
            Building b = city.coords_to_building.get(c);
            if (b == null) continue;

            // on ne remplace que du VOID
            if (b.chars().type == Building.Type.VOID) {
                Building.Characteristics chosen = useful[rnd.nextInt(useful.length)];
                city.setBuilding(c, new Building(chosen));
                return;
            }
        }

        // fallback : si pas de place, on ne fait rien
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

    private static void removeWorstResidenceIfReallyBad(City city) {
        City.Coordinates worst = findWorstResidence(city);
        if (worst == null) return;

        Building bRes = city.buildingAt(worst);
        if (bRes == null || bRes.chars().type != Building.Type.RESIDENTIAL) return;

        List<City.Coordinates> resCells = new ArrayList<>();
        List<City.Coordinates> fireCells = new ArrayList<>();
        List<City.Coordinates> policeCells = new ArrayList<>();
        List<City.Coordinates> healthCells = new ArrayList<>();
        List<City.Coordinates> parkCells = new ArrayList<>();
        List<City.Coordinates> schoolCells = new ArrayList<>();
        List<City.Coordinates> trainCells = new ArrayList<>();
        List<City.Coordinates> factoryCells = new ArrayList<>();

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

        boolean connectedToEntry = isResidentialBuildingWellConnected(bRes, city, connectedRoads);

        double sLoc = localScoreForResidence(
                city, worst,
                fireCells, policeCells, healthCells, parkCells, schoolCells, trainCells, factoryCells,
                connectedToEntry
        );
        if (sLoc < -30.0) {
            city.removeResidentialBuilding(bRes);
        }
    }

    // random mutations
    public static City randomMutation(City city) {
        City nc = city.deepCopy();

        double p = rnd.nextDouble();

        // 25% of mut are extensions of road
        if (p < 0.25) {
            extendRoadFromNetwork(nc);
            return nc;
        }

        // 50% improve local worse residency
        if (p < 0.5) {
            improveWorstResidence(nc);
            return nc;
        }

        if (p < 0.75) {
            removeWorstResidenceIfReallyBad(nc);
            // try to compensate by adding a new RES
            tryAddResidentialNearRoad(nc);
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

    private static boolean isTooCloseToFactory(City city, City.Coordinates c, List<City.Coordinates> factoryCells) {
        for (City.Coordinates f : factoryCells) {
            Building bf = city.coords_to_building.get(f);
            int radius = bf.chars().radius_x;
            if (City.manhattan(c, f) <= radius + 1) {
                return true;
            }
        }
        return false;
    }

    private static void tryAddResidentialNearRoad(City city) {
        List<City.Coordinates> factoryCells = new ArrayList<>();
        for (var e : city.coords_to_building.entrySet()) {
            if (e.getValue().chars().type.getKind() == Building.Kind.FACTORY) {
                factoryCells.add(e.getKey());
            }
        }

        Set<City.Coordinates> connected = connectedRoads(city);
        if (connected.isEmpty()) return;

        List<City.Coordinates> roads = new ArrayList<>(connected);
        Collections.shuffle(roads, rnd);

        for (City.Coordinates road : roads) {
            for (City.Coordinates n : city.neighbors4(road)) {
                Building b = city.coords_to_building.get(n);
                if (b == null) continue;
                if (b.chars().type != Building.Type.VOID) continue;
                if (isTooCloseToFactory(city, n, factoryCells)) continue;

                Building res = new Building(Building.Characteristics.RESIDENTIAL);
                boolean ok = city.setBuilding(n, res);
                if (ok) return; // on en place juste un
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
        connectAllResidencesWithRoads(best);
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
                default -> {}
            }
        }

        // distinct res buildings
        Set<Building> resBuildings = new HashSet<>();
        for (City.Coordinates r : resCells) {
            resBuildings.add(city.coords_to_building.get(r));
        }
        Map<Building, Boolean> resConnected = new HashMap<>();
        for (Building b : resBuildings) {
            resConnected.put(b, isResidentialBuildingWellConnected(b, city, connectedRoads));
        }

        double sum = 0, min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (City.Coordinates r : resCells) {
            Building br = city.coords_to_building.get(r);
            boolean conn = Boolean.TRUE.equals(resConnected.get(br));
            double loc = localScoreForResidence(
                    city, r,
                    fireCells, policeCells, healthCells, parkCells, schoolCells, trainCells, factoryCells,
                    conn
            );
            sum += loc;
            min = Math.min(min, loc);
            max = Math.max(max, loc);
        }
        double avg = resCells.isEmpty() ? 0.0 : sum / resCells.size();

        // coût total
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
}