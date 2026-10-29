package masr.exception;

/** An entity with the same identity already exists. */
public class DuplicateEntityException extends MasrException {
    private static final long serialVersionUID = 1L;
    public DuplicateEntityException(String message) { super(message); }
}
