package in.bushansirgur.cloudshareapi.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Trims whitespace/CRLF from Mongo URI after spring-dotenv loads .env (Windows line endings).
 */
public class MongoUriPropertyNormalizer implements EnvironmentPostProcessor, Ordered {

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String mongoUri = firstNonBlank(
                environment.getProperty("MONGODB_URI"),
                environment.getProperty("spring.data.mongodb.uri")
        );
        if (mongoUri == null || !isValidMongoUri(mongoUri)) {
            return;
        }

        String trimmed = mongoUri.trim();
        Map<String, Object> normalized = new HashMap<>();
        normalized.put("MONGODB_URI", trimmed);
        normalized.put("spring.data.mongodb.uri", trimmed);
        environment.getPropertySources().addFirst(new MapPropertySource("normalizedMongoUri", normalized));
    }

    private static boolean isValidMongoUri(String uri) {
        String trimmed = uri.trim();
        return trimmed.startsWith("mongodb://") || trimmed.startsWith("mongodb+srv://");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
