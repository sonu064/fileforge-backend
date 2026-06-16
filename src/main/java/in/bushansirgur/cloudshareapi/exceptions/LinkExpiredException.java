package in.bushansirgur.cloudshareapi.exceptions;

/** Thrown when a share link has expired or been revoked. Mapped to HTTP 410 Gone. */
public class LinkExpiredException extends RuntimeException {
    public LinkExpiredException(String message) {
        super(message);
    }
}
