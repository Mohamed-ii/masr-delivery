package masr.domain;

public final class Motorcycle implements VehicleType {
    @Override public String name() { return "motorcycle"; }
    @Override public int maxRangeKm() { return 25; }
    @Override public int speedKmh() { return 40; }
    @Override public int maxOrderItems() { return 6; }
    @Override public String toString() { return name(); }
}
