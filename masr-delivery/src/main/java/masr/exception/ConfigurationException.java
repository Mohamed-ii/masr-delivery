package masr.exception;

/** Platform configuration or seed data is unreadable or malformed. */
public class ConfigurationException extends MasrRuntimeException {
    private static final long serialVersionUID = 1L;
    public ConfigurationException(String message) { super(message); }
}
