package masr.domain;

import masr.exception.InvalidInputException;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum District {
    MAADI("Maadi"), DOKKI("Dokki"), FAISAL("Faisal"), NASR_CITY("Nasr City"), HELIOPOLIS("Heliopolis");

    /** Whole-kilometre road distances, symmetric, indexed by ordinal. */
    private static final int[][] KM = {
            {0, 9, 12, 16, 15},
            {9, 0, 6, 14, 13},
            {12, 6, 0, 18, 17},
            {16, 14, 18, 0, 5},
            {15, 13, 17, 5, 0}};

    private final String label;

    District(String label) { this.label = label; }

    public String label() { return label; }
    public int distanceKmTo(District other) { return KM[ordinal()][other.ordinal()]; }

    public static District parse(String s) {
        String t = s == null ? "" : s.trim();
        for (District d : values())
            if (d.label.equalsIgnoreCase(t) || d.name().equalsIgnoreCase(t.replace(' ', '_'))) return d;
        throw new InvalidInputException("Unknown district '" + t + "'. Valid districts: "
                + Arrays.stream(values()).map(District::label).collect(Collectors.joining(", ")));
    }

    @Override public String toString() { return label; }
}
