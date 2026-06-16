package in.bushansirgur.cloudshareapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;

/** Public payload returned when resolving a share link (no owner-only fields). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicShareDTO {
    private String shareId;
    private String name;
    private String type;
    private Long size;
    private LocalDateTime uploadedAt;
    private long viewCount;
    private Instant expiresAt;
    /** True when the viewer must supply a password before previewing/downloading. */
    private boolean passwordProtected;
    // Stream/download URLs relative to the API root.
    private String viewUrl;
    private String downloadUrl;
}
