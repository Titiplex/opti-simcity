package org.titiplex.optimizer;

import org.titiplex.city.Building;
import org.titiplex.city.City;

import java.util.*;

class ResidentialOptimizer {

    static boolean isResidentialBuildingWellConnected(
            Building b,
            City city,
            Set<City.Coordinates> connectedRoads
    ) {
        if (b.chars().type != Building.Type.RESIDENTIAL) return true;

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
     * Local score for a residential building, to guide mutations
     *
     */
    static double localScoreForResidence(
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

        // schools
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

    static void connectAllResidencesWithRoads(City city) {
        boolean changed = true;

        while (changed) {
            changed = false;
            Set<City.Coordinates> connected = GameOptimizer.connectedRoads(city);

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
                        if (t != Building.Type.VOID && t != Building.Type.ROAD && t != Building.Type.CROSSING) continue;

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

    private static City.Coordinates findWorstResidence(City city) {
        List<City.Coordinates> resCells = new ArrayList<>();
        List<City.Coordinates> fireCells = new ArrayList<>();
        List<City.Coordinates> policeCells = new ArrayList<>();
        List<City.Coordinates> healthCells = new ArrayList<>();
        List<City.Coordinates> parkCells = new ArrayList<>();
        List<City.Coordinates> schoolCells = new ArrayList<>();
        List<City.Coordinates> trainCells = new ArrayList<>();
        List<City.Coordinates> factoryCells = new ArrayList<>();

        Set<City.Coordinates> connectedRoads = GameOptimizer.connectedRoads(city);

        GameOptimizer.fillCells(city, resCells, fireCells, policeCells, healthCells, parkCells, schoolCells, trainCells, factoryCells);

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

    static void improveWorstResidence(City city) {
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
        Collections.shuffle(list, GameOptimizer.rnd);

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

            // only void to replace
            if (b.chars().type == Building.Type.VOID) {
                Building.Characteristics chosen = useful[GameOptimizer.rnd.nextInt(useful.length)];
                city.setBuilding(c, new Building(chosen));
                return;
            }
        }

        // fallback : si pas de place, on ne fait rien
    }

    static void removeWorstResidenceIfReallyBad(City city) {
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

        Set<City.Coordinates> connectedRoads = GameOptimizer.connectedRoads(city);

        GameOptimizer.fillCells(city, resCells, fireCells, policeCells, healthCells, parkCells, schoolCells, trainCells, factoryCells);

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

    static boolean isTooCloseToFactory(City city, City.Coordinates c, List<City.Coordinates> factoryCells) {
        for (City.Coordinates f : factoryCells) {
            Building bf = city.coords_to_building.get(f);
            int radius = bf.chars().radius_x;
            if (City.manhattan(c, f) <= radius + 1) {
                return true;
            }
        }
        return false;
    }

    static void tryAddResidentialNearRoad(City city) {
        List<City.Coordinates> factoryCells = new ArrayList<>();
        for (var e : city.coords_to_building.entrySet()) {
            if (e.getValue().chars().type.getKind() == Building.Kind.FACTORY) {
                factoryCells.add(e.getKey());
            }
        }

        Set<City.Coordinates> connected = GameOptimizer.connectedRoads(city);
        if (connected.isEmpty()) return;

        List<City.Coordinates> roads = new ArrayList<>(connected);
        Collections.shuffle(roads, GameOptimizer.rnd);

        for (City.Coordinates road : roads) {
            for (City.Coordinates n : city.neighbors4(road)) {
                Building b = city.coords_to_building.get(n);
                if (b == null) continue;
                if (b.chars().type != Building.Type.VOID) continue;
                if (isTooCloseToFactory(city, n, factoryCells)) continue;

                Building res = new Building(Building.Characteristics.RESIDENTIAL);
                boolean ok = city.setBuilding(n, res);
                if (ok) return;
            }
        }
    }
}
