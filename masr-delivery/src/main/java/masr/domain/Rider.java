package masr.domain;

import masr.common.Validation;
import masr.exception.RiderBusyException;
import masr.exception.RiderUnavailableException;

import java.util.Optional;

/**
 * Identity = id. Holds the platform's most important invariant: at most ONE active order.
 * assign() is the only way an order gets onto a rider, and it checks-and-sets under the rider's lock.
 */
public final class Rider {
    private final String id;
    private final String name;
    private final VehicleType vehicle;
    private District district;
    private RiderStatus status = RiderStatus.OFF_DUTY;
    private int completedDeliveries;
    private Order activeOrder;

    public Rider(String id, String name, VehicleType vehicle, District district) {
        this.id = Validation.id(id, "Rider id");
        this.name = Validation.nonBlank(name, "Rider name");
        this.vehicle = java.util.Objects.requireNonNull(vehicle, "vehicle");
        this.district = java.util.Objects.requireNonNull(district, "district");
    }

    public String id() { return id; }
    public String name() { return name; }
    public VehicleType vehicle() { return vehicle; }
    public synchronized District district() { return district; }
    public synchronized RiderStatus status() { return status; }
    public synchronized int completedDeliveries() { return completedDeliveries; }
    public synchronized void setCompletedDeliveries(int n) { completedDeliveries = n; }
    public synchronized Optional<Order> activeOrder() { return Optional.ofNullable(activeOrder); }
    public synchronized boolean isAvailable() { return status == RiderStatus.AVAILABLE && activeOrder == null; }

    public synchronized void goOnDuty() { if (status == RiderStatus.OFF_DUTY) status = RiderStatus.AVAILABLE; }
    public synchronized void goOffDuty() throws RiderBusyException {
        if (activeOrder != null)
            throw new RiderBusyException("Rider " + id + " still holds order " + activeOrder.id() + " and cannot go off duty");
        status = RiderStatus.OFF_DUTY;
    }

    public synchronized void assign(Order order) throws RiderUnavailableException {
        if (activeOrder != null)
            throw new RiderBusyException("Rider " + id + " is already busy with order " + activeOrder.id());
        if (status != RiderStatus.AVAILABLE)
            throw new RiderUnavailableException("Rider " + id + " is not available (" + status + ")");
        activeOrder = order;
        status = RiderStatus.BUSY;
    }
    public synchronized void release(Order order) {
        if (activeOrder != null && activeOrder.equals(order)) {
            activeOrder = null;
            status = RiderStatus.AVAILABLE;
        }
    }
    public synchronized void completeDelivery(Order order, District deliveredTo) {
        if (activeOrder != null && activeOrder.equals(order)) {
            release(order);
            completedDeliveries++;
            district = deliveredTo;
        }
    }

    /** Can this rider's vehicle handle the order's route length and size? */
    public boolean canServe(Order order) {
        int km = order.restaurant().district().distanceKmTo(order.deliveryDistrict());
        return vehicle.maxRangeKm() >= km && vehicle.maxOrderItems() >= order.itemCount();
    }

    @Override public boolean equals(Object o) { return o instanceof Rider r && r.id.equals(id); }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() { return id + " " + name; }
}
