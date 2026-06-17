package in.bushansirgur.cloudshareapi.config;

/** Masks credentials in a MongoDB connection URI for safe logging. */
public final class MongoUriMasker {

    private MongoUriMasker() {
    }

    public static String mask(String uri) {
        if (uri == null || uri.isBlank()) {
            return "(not set)";
        }
        // mongodb+srv://user:password@host/db?...
        return uri.replaceFirst("://([^:@/]+):([^@/]+)@", "://$1:****@");
    }
}
