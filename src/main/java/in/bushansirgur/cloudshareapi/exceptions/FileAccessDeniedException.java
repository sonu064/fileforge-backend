package in.bushansirgur.cloudshareapi.exceptions;

/** Thrown when a user tries to access a file they do not own. Mapped to HTTP 403. */
public class FileAccessDeniedException extends RuntimeException {
    public FileAccessDeniedException(String message) {
        super(message);
    }
}
