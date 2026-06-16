package in.bushansirgur.cloudshareapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** The single source of truth for a user account (replaces Clerk + the old profiles collection). */
@Document(collection = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDocument {

    @Id
    private String id;

    private String firstName;
    private String lastName;

    @Indexed(unique = true)
    private String email;

    /** BCrypt hash — never store the plaintext password. */
    private String password;

    /** USER or ADMIN. */
    @Builder.Default
    private Role role = Role.USER;

    @Builder.Default
    private Integer credits = 5;

    /** Subscription tier: BASIC, PREMIUM, ULTIMATE. */
    @Builder.Default
    private String plan = "BASIC";

    private String profileImage;

    @Builder.Default
    private boolean emailVerified = false;

    private Instant createdAt;
    private Instant updatedAt;
}
