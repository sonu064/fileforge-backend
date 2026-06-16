package in.bushansirgur.cloudshareapi.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Validates production-only secrets after the application context is ready.
 * This component is not loaded for the {@code dev} profile, so local startup never
 * requires Razorpay keys, SMTP, or a production frontend URL.
 */
@Slf4j
@Component
@Profile("prod")
public class ProductionStartupValidator {

    private final String mongoUri;
    private final String razorpayKeyId;
    private final String razorpayKeySecret;

    public ProductionStartupValidator(
            @Value("${spring.data.mongodb.uri:}") String mongoUri,
            @Value("${razorpay.key.id:}") String razorpayKeyId,
            @Value("${razorpay.key.secret:}") String razorpayKeySecret) {
        this.mongoUri = mongoUri;
        this.razorpayKeyId = razorpayKeyId;
        this.razorpayKeySecret = razorpayKeySecret;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateProductionSecrets() {
        if (!StringUtils.hasText(mongoUri) || mongoUri.contains("localhost")) {
            throw new IllegalStateException(
                    "spring.data.mongodb.uri must be set to a production MongoDB Atlas URI (env: MONGODB_URI)");
        }
        if (!StringUtils.hasText(razorpayKeyId) || !StringUtils.hasText(razorpayKeySecret)) {
            throw new IllegalStateException(
                    "razorpay.key.id and razorpay.key.secret are required in production (env: RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET)");
        }
        log.info("Production secret validation passed");
    }
}
