package org.titiplex.city;

import java.util.*;

public final class City {

    public record Coordinates(int x, int y) {
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Coordinates(int x1, int y1))) return false;
            return x() == x1 && y() == y1;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x(), y());
        }
    }

    public static int manhattan(Coordinates a, Coordinates b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }

    public HashMap<Coordinates, Building> coords_to_building;
    public int width, height;

    public Coordinates start;
    public Coordinates[][] grid;

    public City(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid dimensions");
        this.width = width;
        this.height = height;
        this.grid = new Coordinates[height][width];
        this.coords_to_building = new HashMap<>();

        int yRoad = height / 2;

        // only one building for the whole road
        var mainRoad = new Building(Building.Characteristics.ROAD);
        this.start = new Coordinates(0, yRoad);

        // horizontal road in the middle
        for (int x = 0; x < width; x++) {
            Coordinates c = new Coordinates(x, yRoad);
            grid[yRoad][x] = c;
            coords_to_building.put(c, mainRoad);
            mainRoad.addCoord(c);
        }

        for (int y = 0; y < height; y++) {
            if (y == yRoad) continue; // déjà fait
            for (int x = 0; x < width; x++) {
                Coordinates c = new Coordinates(x, y);
                grid[y][x] = c;
                var newB = new Building(Building.Characteristics.VOID);
                coords_to_building.put(c, newB);
                newB.addCoord(c);
            }
        }
    }

    public boolean inside(Coordinates c) {
        return !c.equals(this.start) && c.x >= 0 && c.x < width && c.y >= 0 && c.y < height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean hasBuilding(Coordinates c) {
        if (!inside(c)) return false;
        return coords_to_building.get(c).chars() != Building.Characteristics.VOID;
    }

    public boolean setBuilding(Coordinates c, Building b) {
        if (!inside(c) || hasBuilding(c)) return false;

        boolean ne_flag = true, se_flag = true, nw_flag = true, sw_flag = true;
        HashMap<String, List<Coordinates>> coords = new HashMap<>();
        coords.put("ne", new ArrayList<>());
        coords.put("se", new ArrayList<>());
        coords.put("nw", new ArrayList<>());
        coords.put("sw", new ArrayList<>());

        for (int y = 0; y < b.chars().y; y++) {
            for (int x = 0; x < b.chars().x; x++) {

                var ne = new Coordinates(c.x + x, c.y + y);
                coords.get("ne").add(ne);
                var se = new Coordinates(c.x + x, c.y - y);
                coords.get("se").add(se);
                var nw = new Coordinates(c.x - x, c.y + y);
                coords.get("nw").add(nw);
                var sw = new Coordinates(c.x - x, c.y - y);
                coords.get("sw").add(sw);
                if (!inside(ne) || hasBuilding(ne)) ne_flag = false;
                if (!inside(se) || hasBuilding(se)) se_flag = false;
                if (!inside(nw) || hasBuilding(nw)) nw_flag = false;
                if (!inside(sw) || hasBuilding(sw)) sw_flag = false;

                if (!ne_flag && !se_flag && !nw_flag && !sw_flag) return false;
            }
        }
        String winner = "";
        if (ne_flag) winner = "ne";
        else if (se_flag) winner = "se";
        else if (nw_flag) winner = "nw";
        else if (sw_flag) winner = "sw";
        for (var cd : coords.get(winner)) {
            coords_to_building.put(cd, b);
            b.coords().add(cd);
        }
        return true;
    }

    public void rmBuilding(Coordinates c) {
        if (!inside(c) || !hasBuilding(c) || !coords_to_building.containsKey(c)) return;
        var temp = coords_to_building.get(c).coords();
        for (var t : temp) {
            coords_to_building.remove(t);
            coords_to_building.put(t, new Building(Building.Characteristics.VOID));
        }
    }

    public List<Coordinates> neighbors4(Coordinates c) {
        int x = c.x;
        int y = c.y;
        List<Coordinates> res = new ArrayList<>(4);
        if (x + 1 < width) res.add(new Coordinates(x + 1, y));
        if (x - 1 >= 0) res.add(new Coordinates(x - 1, y));
        if (y + 1 < height) res.add(new Coordinates(x, y + 1));
        if (y - 1 >= 0) res.add(new Coordinates(x, y - 1));
        return res;
    }

    public City deepCopy() {
        City c = new City(this.width, this.height);
        c.coords_to_building.clear();

        // mapping ancient building -> new building
        var map = new HashMap<Building, Building>();

        for (var entry : this.coords_to_building.entrySet()) {
            Coordinates coord = entry.getKey();
            Building oldB = entry.getValue();

            Building clone = map.computeIfAbsent(oldB, b -> new Building(b.chars()));
            clone.addCoord(coord);

            c.coords_to_building.put(coord, clone);
        }
        c.start = this.start;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                c.grid[y][x] = new Coordinates(x, y);
            }
        }

        return c;
    }

    public static City randomInitialCity(int width, int height, Random rnd) {
        City city = new City(width, height);

        int yRoad = height / 2;
        // residencies close to the road
        int nbRes = (width * height) / 8;
        for (int i = 0; i < nbRes; i++) {
            int x = rnd.nextInt(width);
            int y = yRoad + (rnd.nextBoolean() ? -1 : 1);
            Coordinates c = new Coordinates(x, y);
            if (city.coords_to_building.get(c).chars() == Building.Characteristics.VOID) {
                city.setBuilding(c, new Building(Building.Characteristics.RESIDENTIAL));
            }
        }

        // some public buildings randomly placed
        for (int i = 0; i < 6; i++) {
            Coordinates c = new Coordinates(rnd.nextInt(width), rnd.nextInt(height));
            if (city.coords_to_building.get(c).chars().type == Building.Type.VOID) {
                city.setBuilding(c, new Building(Building.Characteristics.values()[rnd.nextInt(Building.Characteristics.values().length)]));
            }
        }

        return city;
    }

    public List<Coordinates> allCoords() {
        List<Coordinates> res = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                res.add(new Coordinates(x, y));
            }
        }
        return res;
    }

    public void printCity() {
        for (int y = 0; y < this.getHeight(); y++) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < this.getWidth(); x++) {
                Coordinates c = new Coordinates(x, y);
                Building t = this.coords_to_building.get(c);
                char ch = ' ';
                if (t.chars() == Building.Characteristics.VOID) {
                    ch = '.';
                } else {
                    switch (t.chars().type) {
                        case ROAD -> ch = '#';
                        case RAIL -> ch = '=';
                        default -> {
                            switch (t.chars().type.getKind()) {
                                case PARK -> ch = 'p';
                                case POLICE -> ch = 'P';
                                case HEALTH -> ch = 'H';
                                case EDUCATION -> ch = 'E';
                                case FACTORY -> ch = 'F';
                                case TRANSIT -> ch = 'T';
                                case FIRE -> ch = 'f';
                                case RES -> ch = 'r';
                                default -> ch = 'X';
                            }
                        }
                    }
                }
                sb.append(ch).append(' ');
            }
            System.out.println(sb);
        }
        System.out.println();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof City city)) return false;
        return getWidth() == city.getWidth() && getHeight() == city.getHeight() && Objects.equals(coords_to_building, city.coords_to_building) && Objects.equals(start, city.start) && Objects.deepEquals(grid, city.grid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(coords_to_building, getWidth(), getHeight(), start, Arrays.deepHashCode(grid));
    }

    public Building buildingAt(City.Coordinates c) {
        return this.coords_to_building.get(c);
    }

    public void removeResidentialBuilding(Building bRes) {
        if (bRes == null || bRes.chars().type != Building.Type.RESIDENTIAL) return;
        // copy to avoid modification
        var cells = new ArrayList<>(bRes.coords());
        for (City.Coordinates c : cells) {
            this.rmBuilding(c);
        }
    }
}
