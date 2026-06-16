package in.bushansirgur.cloudshareapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileMetadataDTO {
    private String id;
    private String name;
    private String type;
    private Long size;
    private Boolean isPublic;
    private String fileLocation;
    private LocalDateTime uploadedAt;

    // Owner identifier exposed for clarity to the client.
    private String userId;
    private String ownerId;
    // API-relative URLs the client can use to stream/download the file.
    private String viewUrl;
    private String downloadUrl;

    private String folderId;
    private boolean favorite;
    private Instant favoriteAt;
    private List<String> tags;
    /** Category used by the type filter (image/pdf/document/video/audio/archive/executable/other). */
    private String category;
    private boolean deleted;
    private Instant deletedAt;
}
