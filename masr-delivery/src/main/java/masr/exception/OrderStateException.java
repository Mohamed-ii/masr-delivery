package masr.exception;

/** The order is in a state that does not allow the requested operation. */
public class OrderStateException extends MasrException {
    private static final long serialVersionUID = 1L;
    public OrderStateException(String message) { super(message); }
}
