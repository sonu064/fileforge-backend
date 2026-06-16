package in.bushansirgur.cloudshareapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Contents of a folder: the folder itself (null at root), its breadcrumb path, subfolders and files. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderContentsDTO {
    private FolderDTO currentFolder;
    private List<FolderDTO> breadcrumb;
    private List<FolderDTO> folders;
    private List<FileMetadataDTO> files;
}
