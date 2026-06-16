package in.bushansirgur.cloudshareapi.dto;

import lombok.Data;

import java.time.Instant;

/**
 * Request body for creating a share link.
 * expiry/expiration: one of "1h", "24h", "7d", "30d", "never", or "custom".
 * When "custom", customExpiresAt must be provided.
 * password (optional): when present the link becomes password-protected (stored BCrypt-hashed).
 */
@Data
public class CreateShareRequest {
    private String expiration = "never";
    /** Alias for {@link #expiration} matching the documented API ("expiry"). */
    private String expiry;
    private Instant customExpiresAt;
    private String password;

    /** Returns the effective expiry option, preferring {@code expiry} when supplied. */
    public String effectiveExpiry() {
        if (expiry != null && !expiry.isBlank()) return expiry;
        return expiration;
    }
}
