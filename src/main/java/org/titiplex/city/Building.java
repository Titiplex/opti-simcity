package org.titiplex.city;

import java.util.HashSet;
import java.util.Objects;

public record Building(Characteristics chars, HashSet<City.Coordinates> coords) {

    public Building(Characteristics chars) {
        this(chars, new HashSet<>());
    }

    public void addCoord(City.Coordinates coord) {
        coords.add(coord);
    }

    public enum Kind {
        VOID,
        RES,
        TRANSIT,
        FIRE,
        POLICE,
        HEALTH,
        PARK,
        EDUCATION,
        FACTORY
    }

    public enum Type {
        VOID(Kind.VOID),
        RESIDENTIAL(Kind.RES),
        ROAD(Kind.TRANSIT),
        RAIL(Kind.TRANSIT),
        CROSSING(Kind.TRANSIT),
        POLICE_STATION(Kind.POLICE),
        FIRE_STATION(Kind.FIRE),
        HEALTH_CLINIC(Kind.HEALTH),
        RAILWAY_STATION(Kind.TRANSIT),
        SCHOOL(Kind.EDUCATION),
        FACTORY(Kind.FACTORY),
        PARK(Kind.PARK);

        private final Kind kind;

        Type(Kind kind) {
            this.kind = kind;
        }

        public Kind getKind() {
            return kind;
        }
    }

    public enum Characteristics {
        // MISC
        VOID(Type.VOID, 1, 1, 0, false, false, false),
        RESIDENTIAL(Type.RESIDENTIAL, 2, 2, 0, false, true, false),
        ROAD(Type.ROAD, 1, 1, 0, false, true, false),
        RAIL(Type.RAIL, 1, 1, 0, false, false, true),
        CROSSING(Type.CROSSING, 1, 1, 0, false, false, false),

        // SERVICES
        SMALL_POLICE_STATION(Type.POLICE_STATION, 1, 1, 6, 8, false, true, false),
        BASIC_POLICE_STATION(Type.POLICE_STATION, 2, 2, 12, false, true, false),
        POLICE_PRECINCT(Type.POLICE_STATION, 4, 2, 24, false, true, false),
        SMALL_FIRE_STATION(Type.FIRE_STATION, 1, 1, 6, 8, false, true, false),
        BASIC_FIRE_STATION(Type.FIRE_STATION, 2, 2, 10, 12, false, true, false),
        DELUXE_FIRE_STATION(Type.FIRE_STATION, 4, 2, 22, false, true, false),
        SMALL_HEALTH_CLINIC(Type.HEALTH_CLINIC, 1, 1, 8, false, true, false),
        BASIC_HEALTH_CLINIC(Type.HEALTH_CLINIC, 2, 2, 12, false, true, false),
        HOSPITAL(Type.HEALTH_CLINIC, 4, 2, 24, 24, false, true, false),

        // TRANSPORT
        SMALL_RAILWAY_STATION(Type.RAILWAY_STATION, 2, 3, 8, false, true, true),
        MEDIUM_RAILWAY_STATION(Type.RAILWAY_STATION, 4, 3, 10, false, true, true),
        CENTRAL_RAILWAY_STATION(Type.RAILWAY_STATION, 6, 3, 16, false, true, true),

        // EDUCATION
        DPT_EDUCATION(Type.SCHOOL, 2, 3, 8, false, true, false),
        NURSERY_SCHOOL(Type.SCHOOL, 2, 2, 8, false, true, false),
        GRADE_SCHOOL(Type.SCHOOL, 3, 2, 10, 8, false, true, false),
        PUBLIC_LIBRAIRY(Type.SCHOOL, 2, 2, 12, 8, false, true, false),
        HIGH_SCHOOL(Type.SCHOOL, 4, 2, 14, 12, false, true, false),
        COMMUNITY_COLLEGE(Type.SCHOOL, 4, 3, 16, false, true, false),
        UNIVERSITY(Type.SCHOOL, 4, 4, 22, false, true, false),

        // NATURE
        SMALL_FOUNTAIN_PARK(Type.PARK, 1, 1, 8, false, true, false),
        ;
        public final Type type;
        public final int x, y;
        public final int radius_x;
        public final int radius_y;
        public final boolean isRadiusNegative;
        public final boolean isNextToRoad;
        public final boolean isNextToRail;

        Characteristics(Type type, int x, int y, int radius_x, int radius_y, boolean isNegativeRadius, boolean isNextToRoad, boolean isNextToRail) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.radius_x = radius_x;
            this.radius_y = radius_y;
            this.isRadiusNegative = isNegativeRadius;
            this.isNextToRoad = isNextToRoad;
            this.isNextToRail = isNextToRail;
        }

        Characteristics(Type type, int x, int y, int radius, boolean isNegativeRadius, boolean isNextToRoad, boolean isNextToRail) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.radius_x = radius;
            this.radius_y = radius;
            this.isRadiusNegative = isNegativeRadius;
            this.isNextToRoad = isNextToRoad;
            this.isNextToRail = isNextToRail;
        }
    }

    public double getCost() {
        // TODO adjust to real cost in simoleons
        return switch (this.chars()) {
            case RESIDENTIAL -> 50.0;
            case ROAD, RAIL -> 1.0;
            case CROSSING -> 3.0;
            case SMALL_POLICE_STATION, SMALL_FIRE_STATION, SMALL_HEALTH_CLINIC -> 80.0;
            case BASIC_POLICE_STATION, BASIC_FIRE_STATION, BASIC_HEALTH_CLINIC -> 150.0;
            case HOSPITAL, POLICE_PRECINCT, DELUXE_FIRE_STATION, MEDIUM_RAILWAY_STATION -> 300.0;
            case SMALL_RAILWAY_STATION, HIGH_SCHOOL, COMMUNITY_COLLEGE -> 200.0;
            case CENTRAL_RAILWAY_STATION -> 500.0;
            case DPT_EDUCATION, NURSERY_SCHOOL, GRADE_SCHOOL, PUBLIC_LIBRAIRY -> 120.0;
            case UNIVERSITY -> 400.0;
            case SMALL_FOUNTAIN_PARK -> 40.0;
            case VOID -> 0.0;
        };
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Building(Characteristics chars1, HashSet<City.Coordinates> coords1))) return false;
        return chars() == chars1 && Objects.equals(coords(), coords1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chars(), coords());
    }

    public static boolean sameChars(Building a, Building b) {
        return a.chars().equals(b.chars());
    }
}