package masr.domain;

public final class Bicycle implements VehicleType {
    @Override public String name() { return "bicycle"; }
    @Override public int maxRangeKm() { return 6; }
    @Override public int speedKmh() { return 15; }
    @Override public int maxOrderItems() { return 3; }
    @Override public String toString() { return name(); }
}
