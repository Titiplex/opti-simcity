package org.titiplex.optimizer;

import org.titiplex.city.Building;
import org.titiplex.city.City;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class CoverageOptimizer {
    static int countCoveredResidencesForService(
            Building serviceBuilding,
            List<City.Coordinates> resCells
    ) {
        Building.Characteristics chars = serviceBuilding.chars();
        int rx = chars.radius_x;
        int ry = chars.radius_y;
        int count = 0;

        // center = first coordinates of building
        if (serviceBuilding.coords().isEmpty()) return 0;
        City.Coordinates center = serviceBuilding.coords().iterator().next();

        for (City.Coordinates r : resCells) {
            int dx = Math.abs(r.x() - center.x());
            int dy = Math.abs(r.y() - center.y());
            if (dx <= rx && dy <= ry) {
                count++;
            }
        }
        return count;
    }

    static void removeUselessServices(City city) {
        List<City.Coordinates> resCells = new ArrayList<>();
        for (var e : city.coords_to_building.entrySet()) {
            if (e.getValue().chars().type.getKind() == Building.Kind.RES) {
                resCells.add(e.getKey());
            }
        }
        if (resCells.isEmpty()) return;

        // services candidates
        Set<Building> services = new HashSet<>();
        for (Building b : city.coords_to_building.values()) {
            Building.Type t = b.chars().type;
            if (t == Building.Type.POLICE_STATION ||
                    t == Building.Type.FIRE_STATION ||
                    t == Building.Type.HEALTH_CLINIC ||
                    t == Building.Type.SCHOOL ||
                    t == Building.Type.PARK ||
                    t == Building.Type.RAILWAY_STATION) {
                services.add(b);
            }
        }

        // delete null service
        Building worst = null;
        int bestScore = Integer.MAX_VALUE; // plus petit = pire
        for (Building s : services) {
            int covered = countCoveredResidencesForService(s, resCells);
            if (covered < bestScore) {
                bestScore = covered;
                worst = s;
            }
        }

        // threshold : doesn't cover anyone or close to anyone
        if (worst != null && bestScore <= 1) {
            // remove whole building
            var cells = new ArrayList<>(worst.coords());
            for (City.Coordinates c : cells) {
                city.rmBuilding(c);
            }
        }
    }
}
