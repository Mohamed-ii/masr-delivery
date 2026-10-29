package masr.exception;

/** The delivery address does not belong to the customer. */
public class AddressNotOwnedException extends MasrException {
    private static final long serialVersionUID = 1L;
    public AddressNotOwnedException(String message) { super(message); }
}
