package masr.domain;

import masr.exception.InvalidInputException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Registry so new vehicle kinds plug in without touching Rider or the dispatcher. */
public final class VehicleTypes {
    private static final Map<String, VehicleType> REGISTRY = new LinkedHashMap<>();
    static {
        register(new Motorcycle());
        register(new Bicycle());
        register(new Car());
    }
    private VehicleTypes() { }

    public static synchronized void register(VehicleType t) { REGISTRY.put(t.name().toLowerCase(Locale.ROOT), t); }
    public static synchronized List<VehicleType> all() { return new ArrayList<>(REGISTRY.values()); }
    public static synchronized VehicleType parse(String s) {
        VehicleType t = REGISTRY.get(s == null ? "" : s.trim().toLowerCase(Locale.ROOT));
        if (t == null) throw new InvalidInputException("Unknown vehicle type '" + s + "'. Known: " + REGISTRY.keySet());
        return t;
    }
}
