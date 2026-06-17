package in.bushansirgur.cloudshareapi.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifies Atlas connectivity after startup: DNS, TCP, and MongoDB ping.
 * Does not block application boot — login and other DB operations still require a healthy cluster.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoConnectionHealthCheck {

    private static final Pattern SRV_HOST = Pattern.compile("@([^/?]+)");
    private static final int TCP_TIMEOUT_MS = 5_000;

    private final MongoTemplate mongoTemplate;
    private final Environment environment;

    @Value("${spring.data.mongodb.uri:}")
    private String mongoUri;

    @EventListener(ApplicationReadyEvent.class)
    @Order(0)
    public void verifyConnection() {
        log.info("Active profile={}", Arrays.toString(environment.getActiveProfiles()));
        log.info("Java version={}", System.getProperty("java.version"));
        logTlsProtocols();

        boolean loaded = StringUtils.hasText(mongoUri);
        log.info("MONGODB_URI loaded={}", loaded);
        log.info("Mongo URI={}", MongoUriMasker.mask(mongoUri));

        if (!loaded) {
            log.error("MONGODB_URI is missing — add it to cloudshareapi/.env");
            return;
        }

        String atlasHost = parseHost(mongoUri);
        if (atlasHost != null) {
            log.info("Atlas hostname={}", atlasHost);
            if (mongoUri.startsWith("mongodb+srv://")) {
                checkSrvRecords(atlasHost);
            } else {
                checkDns(atlasHost);
            }
        }

        checkPing();
    }

    private static void logTlsProtocols() {
        try {
            log.info("TLS default protocols={}",
                    Arrays.toString(javax.net.ssl.SSLContext.getDefault().getSupportedSSLParameters().getProtocols()));
        } catch (Exception e) {
            log.warn("Could not read TLS protocols: {}", e.getMessage());
        }
    }

    private void checkDns(String host) {
        try {
            List<String> addresses = new ArrayList<>();
            for (InetAddress address : InetAddress.getAllByName(host)) {
                addresses.add(address.getHostAddress());
            }
            log.info("DNS resolution for {} → {}", host, addresses);
        } catch (Exception e) {
            log.error("DNS resolution failed for {}: {}", host, e.getMessage());
        }
    }

    private void checkSrvRecords(String srvHost) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            InitialDirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes("_mongodb._tcp." + srvHost, new String[]{"SRV"});
            Attribute srv = attrs.get("SRV");
            if (srv == null) {
                log.warn("No SRV records found for _mongodb._tcp.{}", srvHost);
                return;
            }

            List<String> targets = new ArrayList<>();
            for (int i = 0; i < srv.size(); i++) {
                String record = srv.get(i).toString().trim();
                String[] parts = record.split("\\s+");
                if (parts.length >= 4) {
                    int port = Integer.parseInt(parts[2]);
                    String target = parts[3].replaceAll("\\.$", "");
                    targets.add(target + ":" + port);
                    checkTcp(target, port);
                }
            }
            log.info("SRV targets for {} → {}", srvHost, targets);
        } catch (NamingException e) {
            log.error("SRV lookup failed for {}: {}", srvHost, e.getMessage());
        }
    }

    private void checkTcp(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), TCP_TIMEOUT_MS);
            log.info("TCP {}:{} reachable={}", host, port, socket.isConnected());
        } catch (Exception e) {
            log.warn("TCP {}:{} failed: {}", host, port, e.getMessage());
        }
    }

    private void checkPing() {
        try {
            Document ping = mongoTemplate.getDb().runCommand(new Document("ping", 1));
            log.info("MongoDB ping successful: {}", ping.toJson());
        } catch (Exception e) {
            log.error("MongoDB ping FAILED: {} — {}", e.getClass().getSimpleName(), rootMessage(e));
            if (containsSslInternalError(e)) {
                log.error(
                        "TLS handshake rejected on all Atlas shards (SSL internal_error). "
                                + "This is not a bad URI or Java truststore — Atlas Network Access is blocking your IP. "
                                + "Fix: MongoDB Atlas → Network Access → Add Current IP Address, then restart.");
            }
        }
    }

    private static String parseHost(String uri) {
        Matcher matcher = SRV_HOST.matcher(uri);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static boolean containsSslInternalError(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof javax.net.ssl.SSLException ssl) {
                String msg = ssl.getMessage();
                return msg != null && msg.contains("internal_error");
            }
            current = current.getCause();
        }
        return false;
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
