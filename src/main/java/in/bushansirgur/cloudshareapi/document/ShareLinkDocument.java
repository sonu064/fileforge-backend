package in.bushansirgur.cloudshareapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** A public, optionally-expiring share link pointing at a single file. */
@Document(collection = "share_links")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareLinkDocument {

    @Id
    private String id;

    /** Public, unguessable identifier used in the share URL. */
    @Indexed(unique = true)
    private String shareId;

    /** The file this link grants access to. */
    private String fileId;

    /** Clerk id of the user who created the link. */
    private String ownerId;

    /** Null = never expires. */
    private Instant expiresAt;

    /** Soft switch — set false when revoked or swept after expiry. */
    private boolean active;

    private long viewCount;

    private Instant createdAt;

    /** True when the link requires a password to access. */
    private boolean passwordProtected;

    /** BCrypt hash of the share password. Never store the plaintext. */
    private String sharePassword;
}
