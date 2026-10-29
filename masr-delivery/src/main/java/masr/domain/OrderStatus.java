package masr.domain;

/** Order lifecycle. The legal-transition rule lives here, next to the states, in one place. */
public enum OrderStatus {
    PLACED, ACCEPTED, PREPARING, READY, ASSIGNED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED;

    public boolean canTransitionTo(OrderStatus next) {
        if (isTerminal()) return false;
        if (next == CANCELLED) return ordinal() < OUT_FOR_DELIVERY.ordinal();
        return next != CANCELLED && next.ordinal() == ordinal() + 1;
    }
    public boolean isTerminal() { return this == DELIVERED || this == CANCELLED; }
}
