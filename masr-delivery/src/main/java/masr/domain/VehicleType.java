package masr.domain;

/** Strategy: a vehicle describes its own dispatch behaviour. Add a scooter = add one class + register it. */
public interface VehicleType {
    String name();
    int maxRangeKm();
    int speedKmh();
    int maxOrderItems();
}
