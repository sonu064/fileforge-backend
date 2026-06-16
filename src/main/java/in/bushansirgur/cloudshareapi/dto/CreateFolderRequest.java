package in.bushansirgur.cloudshareapi.dto;

import lombok.Data;

@Data
public class CreateFolderRequest {
    private String folderName;
    private String parentFolderId;
}
