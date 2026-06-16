package in.bushansirgur.cloudshareapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderDTO {
    private String id;
    private String folderName;
    private String userId;
    private String parentFolderId;
    private Instant createdAt;
    private Instant updatedAt;
    /** Number of immediate children (subfolders + files), for UI hints. */
    private long itemCount;
}
