package in.bushansirgur.cloudshareapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "folders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderDocument {

    @Id
    private String id;

    private String folderName;

    @Indexed
    private String userId;

    /** Null = top-level folder. */
    private String parentFolderId;

    private Instant createdAt;
    private Instant updatedAt;
}
