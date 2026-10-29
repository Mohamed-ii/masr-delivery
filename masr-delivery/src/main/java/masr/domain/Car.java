package masr.domain;

public final class Car implements VehicleType {
    @Override public String name() { return "car"; }
    @Override public int maxRangeKm() { return 40; }
    @Override public int speedKmh() { return 35; }
    @Override public int maxOrderItems() { return 12; }
    @Override public String toString() { return name(); }
}
