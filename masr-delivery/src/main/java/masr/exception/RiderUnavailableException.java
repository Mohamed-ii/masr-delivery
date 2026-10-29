package masr.exception;

/** The rider cannot take an order right now. */
public class RiderUnavailableException extends MasrException {
    private static final long serialVersionUID = 1L;
    public RiderUnavailableException(String message) { super(message); }
}
