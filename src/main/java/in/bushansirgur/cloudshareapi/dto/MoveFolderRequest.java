package in.bushansirgur.cloudshareapi.dto;

import lombok.Data;

@Data
public class MoveFolderRequest {
    /** Target parent folder id; null moves the folder to the root. */
    private String parentFolderId;
}
