package in.bushansirgur.cloudshareapi.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ensures TTL and lookup indexes exist for token and payment collections.
 * <p>
 * Idempotent: skips creation when an index on the same key pattern already exists
 * (e.g. created earlier by {@code @Indexed} + {@code auto-index-creation}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoIndexConfig {

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void ensureIndexes() {
        ensureTtlIndex("refresh_tokens");
        ensureTtlIndex("email_verification_tokens");
        ensureTtlIndex("password_reset_tokens");

        ensureIndexSafe("payment_transactions",
                new Index().on("orderId", Sort.Direction.ASC).unique().sparse().named("orderId_unique_sparse"));
        ensureIndexSafe("payment_transactions",
                new Index().on("userId", Sort.Direction.ASC).named("userId_1"));
        ensureIndexSafe("files",
                new Index().on("userId", Sort.Direction.ASC).named("userId_1"));
        ensureIndexSafe("folders",
                new Index().on("userId", Sort.Direction.ASC).named("userId_1"));

        log.info("MongoDB index ensure completed");
    }

    private void ensureTtlIndex(String collection) {
        ensureIndexSafe(collection,
                new Index().on("expiresAt", Sort.Direction.ASC).expire(0).named("expiresAt_ttl"));
    }

    private void ensureIndexSafe(String collection, Index index) {
        IndexOperations indexOps = mongoTemplate.indexOps(collection);
        Document keys = index.getIndexKeys();
        String indexName = resolveIndexName(index);

        try {
            List<IndexInfo> existing = indexOps.getIndexInfo();

            if (hasEquivalentIndex(existing, index)) {
                log.debug("Skipping index on {} — equivalent keys already present: {}", collection, keys);
                return;
            }

            if (hasIndexWithName(existing, indexName)) {
                log.warn("Skipping index on {} — name '{}' already used with different keys: {}",
                        collection, indexName, keys);
                return;
            }

            indexOps.ensureIndex(index);
            log.info("Created index on {}: {} ({})", collection, keys, indexName);

        } catch (DataAccessException e) {
            log.warn("Could not ensure index on {} ({}): {}", collection, keys, e.getMessage());
        } catch (Exception e) {
            log.warn("Unexpected error ensuring index on {} ({}): {}", collection, keys, e.getMessage());
        }
    }

    private static String resolveIndexName(Index index) {
        Document options = index.getIndexOptions();
        if (options.containsKey("name")) {
            return options.getString("name");
        }
        return index.getIndexKeys().keySet().iterator().next() + "_1";
    }

    private static boolean hasIndexWithName(List<IndexInfo> existing, String name) {
        return existing.stream().anyMatch(info -> name.equals(info.getName()));
    }

    /** True when an existing index covers the same fields, directions, and TTL expiry. */
    private static boolean hasEquivalentIndex(List<IndexInfo> existing, Index desired) {
        Document desiredKeys = desired.getIndexKeys();
        Long desiredExpireSeconds = desiredExpireSeconds(desired);

        for (IndexInfo info : existing) {
            if ("_id_".equals(info.getName())) {
                continue;
            }
            if (!keysMatch(info.getIndexFields(), desiredKeys)) {
                continue;
            }
            if (!expireMatches(info, desiredExpireSeconds)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static boolean keysMatch(List<IndexField> fields, Document desiredKeys) {
        if (fields.size() != desiredKeys.size()) {
            return false;
        }
        for (IndexField field : fields) {
            String key = field.getKey();
            if (!desiredKeys.containsKey(key)) {
                return false;
            }
            int desiredDirection = desiredKeys.getInteger(key);
            int actualDirection = field.getDirection().isAscending() ? 1 : -1;
            if (desiredDirection != actualDirection) {
                return false;
            }
        }
        return true;
    }

    private static Long desiredExpireSeconds(Index index) {
        Document options = index.getIndexOptions();
        if (!options.containsKey("expireAfterSeconds")) {
            return null;
        }
        return options.getLong("expireAfterSeconds");
    }

    private static boolean expireMatches(IndexInfo existing, Long desiredExpireSeconds) {
        if (desiredExpireSeconds == null) {
            return existing.getExpireAfter().isEmpty();
        }
        return existing.getExpireAfter()
                .map(duration -> duration.getSeconds() == desiredExpireSeconds)
                .orElse(false);
    }
}
