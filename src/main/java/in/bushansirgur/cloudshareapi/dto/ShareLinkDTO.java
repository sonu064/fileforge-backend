package in.bushansirgur.cloudshareapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Share link details returned to the owner. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareLinkDTO {
    private String shareId;
    private String fileId;
    private String fileName;
    private Instant expiresAt;
    private boolean active;
    private boolean expired;
    private boolean passwordProtected;
    private long viewCount;
    private Instant createdAt;
}
