package masr.exception;

/** Base of unchecked platform failures: caller or configuration bugs that a correct caller prevents up front. */
public class MasrRuntimeException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public MasrRuntimeException(String message) { super(message); }
}
