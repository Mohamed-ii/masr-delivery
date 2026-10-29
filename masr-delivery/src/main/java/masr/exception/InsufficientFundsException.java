package masr.exception;

/** Wallet balance is too low. */
public class InsufficientFundsException extends MasrException {
    private static final long serialVersionUID = 1L;
    public InsufficientFundsException(String message) { super(message); }
}
