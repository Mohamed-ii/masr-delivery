package masr.domain;

import masr.common.Validation;

/** Value object; equality includes the owning customer, so an address "belongs" to exactly one customer. */
public record Address(String customerId, District district, String detail) {
    public Address {
        customerId = Validation.id(customerId, "Customer id");
        if (district == null) throw new masr.exception.InvalidInputException("District is required");
        detail = Validation.nonBlank(detail, "Address detail");
    }
    @Override public String toString() { return detail + ", " + district; }
}
