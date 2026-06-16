package in.bushansirgur.cloudshareapi.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/** Fails fast in production when JWT_SECRET is missing or too weak. */
@Slf4j
@Component
public class JwtStartupValidator {

    private static final int MIN_SECRET_LENGTH = 64;

    private final String jwtSecret;
    private final Environment environment;

    public JwtStartupValidator(@Value("${jwt.secret}") String jwtSecret, Environment environment) {
        this.jwtSecret = jwtSecret;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateJwtSecret() {
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is required but not set");
        }
        if (jwtSecret.length() < MIN_SECRET_LENGTH) {
            String message = "JWT_SECRET must be at least " + MIN_SECRET_LENGTH + " characters for HS256";
            if (prod) {
                throw new IllegalStateException(message);
            }
            log.warn("{} — acceptable for local dev only", message);
        }
        if (prod && (jwtSecret.contains("dev-only") || jwtSecret.contains("change-this"))) {
            throw new IllegalStateException("JWT_SECRET must not use default or dev placeholder values in production");
        }
    }
}
