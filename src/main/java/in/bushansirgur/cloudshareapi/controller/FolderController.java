package in.bushansirgur.cloudshareapi.controller;

import in.bushansirgur.cloudshareapi.dto.CreateFolderRequest;
import in.bushansirgur.cloudshareapi.dto.FolderContentsDTO;
import in.bushansirgur.cloudshareapi.dto.FolderDTO;
import in.bushansirgur.cloudshareapi.dto.MoveFolderRequest;
import in.bushansirgur.cloudshareapi.dto.RenameFolderRequest;
import in.bushansirgur.cloudshareapi.service.FolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    @PostMapping("/create")
    public ResponseEntity<FolderDTO> createFolder(@RequestBody CreateFolderRequest request) {
        return ResponseEntity.ok(folderService.createFolder(request));
    }

    @GetMapping
    public ResponseEntity<List<FolderDTO>> listFolders() {
        return ResponseEntity.ok(folderService.listFolders());
    }

    @GetMapping("/{id}/contents")
    public ResponseEntity<FolderContentsDTO> getContents(@PathVariable String id) {
        return ResponseEntity.ok(folderService.getContents(id));
    }

    /** Contents of the root level (folders + files with no parent). */
    @GetMapping("/root/contents")
    public ResponseEntity<FolderContentsDTO> getRootContents() {
        return ResponseEntity.ok(folderService.getContents(null));
    }

    @PutMapping("/{id}/rename")
    public ResponseEntity<FolderDTO> renameFolder(@PathVariable String id,
                                                  @RequestBody RenameFolderRequest request) {
        return ResponseEntity.ok(folderService.renameFolder(id, request.getFolderName()));
    }

    @PutMapping("/{id}/move")
    public ResponseEntity<FolderDTO> moveFolder(@PathVariable String id,
                                                @RequestBody MoveFolderRequest request) {
        return ResponseEntity.ok(folderService.moveFolder(id, request.getParentFolderId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFolder(@PathVariable String id) {
        folderService.deleteFolder(id);
        return ResponseEntity.noContent().build();
    }
}
