package masr.exception;

/** The rider already holds an active order. */
public class RiderBusyException extends RiderUnavailableException {
    private static final long serialVersionUID = 1L;
    public RiderBusyException(String message) { super(message); }
}
