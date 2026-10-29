package masr.exception;

/** A value violates a validation rule (bad mobile number, non-positive price, rating out of range...). */
public class InvalidInputException extends MasrRuntimeException {
    private static final long serialVersionUID = 1L;
    public InvalidInputException(String message) { super(message); }
}
