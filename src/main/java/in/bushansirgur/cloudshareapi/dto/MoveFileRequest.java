package in.bushansirgur.cloudshareapi.dto;

import lombok.Data;

import java.util.List;

@Data
public class MoveFileRequest {
    /** Target folder id; null moves the file(s) to the root. */
    private String folderId;
    /** Optional: for bulk move. When present, fileIds take precedence over a path variable. */
    private List<String> fileIds;
}
