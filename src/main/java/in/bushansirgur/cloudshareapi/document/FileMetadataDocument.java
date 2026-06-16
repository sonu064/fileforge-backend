package in.bushansirgur.cloudshareapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "files")
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class FileMetadataDocument {

    @Id
    private String id;
    private String name;
    private String type;
    private Long size;
    @Indexed
    private String userId;
    private Boolean isPublic;
    private String fileLocation;
    private LocalDateTime uploadedAt;

    /** Null = lives at the root (no folder). */
    private String folderId;

    private boolean favorite;
    private Instant favoriteAt;

    private List<String> tags;

    /** Recycle-bin flag. Soft-deleted files are hidden from normal lists. */
    private boolean deleted;
    private Instant deletedAt;
}
