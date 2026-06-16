package in.bushansirgur.cloudshareapi.exceptions;

/** Thrown when a requested resource (e.g. a file) does not exist. Mapped to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
