package masr.exception;

/** The restaurant is closed. */
public class RestaurantClosedException extends MasrException {
    private static final long serialVersionUID = 1L;
    public RestaurantClosedException(String message) { super(message); }
}
