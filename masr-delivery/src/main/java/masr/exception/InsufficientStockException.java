package masr.exception;

/** Requested quantity exceeds remaining daily stock. */
public class InsufficientStockException extends MasrException {
    private static final long serialVersionUID = 1L;
    public InsufficientStockException(String message) { super(message); }
}
