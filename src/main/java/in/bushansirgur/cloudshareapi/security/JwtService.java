package in.bushansirgur.cloudshareapi.security;

import in.bushansirgur.cloudshareapi.document.UserDocument;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/** Issues and validates the application's own HS256 JWTs. */
@Service
public class JwtService {

    private final SecretKey key;
    private final long accessExpirationMs;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.access-expiration-ms}") long accessExpirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs = accessExpirationMs;
    }

    public String generateAccessToken(UserDocument user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessExpirationMs);
        return Jwts.builder()
                .setSubject(user.getId())
                .claim("email", user.getEmail())
                .claim("role", user.getRole() == null ? "USER" : user.getRole().name())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .setAllowedClockSkewSeconds(30)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
