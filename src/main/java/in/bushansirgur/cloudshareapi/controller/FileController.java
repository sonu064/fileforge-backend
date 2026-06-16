package in.bushansirgur.cloudshareapi.controller;

import in.bushansirgur.cloudshareapi.document.FileMetadataDocument;
import in.bushansirgur.cloudshareapi.document.UserDocument;
import in.bushansirgur.cloudshareapi.dto.BulkFileRequest;
import in.bushansirgur.cloudshareapi.dto.FileMetadataDTO;
import in.bushansirgur.cloudshareapi.dto.MoveFileRequest;
import in.bushansirgur.cloudshareapi.service.FileMetadataService;
import in.bushansirgur.cloudshareapi.service.FileStreamingHelper;
import in.bushansirgur.cloudshareapi.service.UserCreditsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/files")
public class FileController {

    private final FileMetadataService fileMetadataService;
    private final UserCreditsService userCreditsService;
    private final FileStreamingHelper fileStreamingHelper;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFiles(@RequestPart("files") MultipartFile[] files) throws IOException {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("Upload request received");
        log.info("User ID: {}", userId);
        log.info("File count: {}", files != null ? files.length : 0);

        if (files == null || files.length == 0) {
            log.warn("Upload rejected — no files in request for user {}", userId);
            throw new RuntimeException("No files were uploaded. Select at least one file.");
        }

        for (MultipartFile file : files) {
            log.info("Filename: {}, size: {} bytes, contentType: {}",
                    file.getOriginalFilename(), file.getSize(), file.getContentType());
        }

        Map<String, Object> response = new HashMap<>();
        List<FileMetadataDTO> list = fileMetadataService.uploadFiles(files);

        UserDocument finalCredits = userCreditsService.getUserCredits();

        response.put("files", list);
        response.put("remainingCredits", finalCredits.getCredits());
        log.info("Upload successful for user {} — {} file(s), {} credits remaining",
                userId, list.size(), finalCredits.getCredits());
        return ResponseEntity.ok(response);
    }

    /**
     * List files owned by the authenticated user.
     * Optional filters: folderId (null=all, "root"=top level, otherwise a folder id),
     * type (image/pdf/document/video/audio/archive/executable/other), favorite=true.
     */
    @GetMapping
    public ResponseEntity<List<FileMetadataDTO>> getFiles(
            @RequestParam(required = false) String folderId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "false") boolean favorite) {
        return ResponseEntity.ok(fileMetadataService.getFiles(folderId, type, favorite));
    }

    /** Backward-compatible alias for the full file list. */
    @GetMapping("/my")
    public ResponseEntity<List<FileMetadataDTO>> getFilesForCurrentUser() {
        return ResponseEntity.ok(fileMetadataService.getFiles(null, null, false));
    }

    /** Files the user has starred. */
    @GetMapping("/favorites")
    public ResponseEntity<List<FileMetadataDTO>> getFavorites() {
        return ResponseEntity.ok(fileMetadataService.getFavorites());
    }

    /** Files currently in the recycle bin. */
    @GetMapping("/trash")
    public ResponseEntity<List<FileMetadataDTO>> getTrash() {
        return ResponseEntity.ok(fileMetadataService.getTrash());
    }

    /** Single file metadata (owner only). */
    @GetMapping("/{id}")
    public ResponseEntity<FileMetadataDTO> getFile(@PathVariable String id) {
        return ResponseEntity.ok(fileMetadataService.getOwnedFile(id));
    }

    /** Stream a file inline for in-app preview (images, PDFs, text). Owner only. */
    @GetMapping("/view/{id}")
    public ResponseEntity<Resource> viewFile(@PathVariable String id) {
        return fileStreamingHelper.stream(fileMetadataService.getOwnedFileEntity(id), true);
    }

    /** Download a file as an attachment. Owner only. */
    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> download(@PathVariable String id) {
        return fileStreamingHelper.stream(fileMetadataService.getOwnedFileEntity(id), false);
    }

    /** Public metadata for a shared file (no auth). */
    @GetMapping("/public/{id}")
    public ResponseEntity<FileMetadataDTO> getPublicFile(@PathVariable String id) {
        return ResponseEntity.ok(fileMetadataService.getPublicFile(id));
    }

    /** Stream a public file inline (no auth) — only works if the file is public. */
    @GetMapping("/public/{id}/view")
    public ResponseEntity<Resource> viewPublicFile(@PathVariable String id) {
        return fileStreamingHelper.stream(fileMetadataService.getPublicFileEntity(id), true);
    }

    /** Download a public file (no auth) — only works if the file is public. */
    @GetMapping("/public/{id}/download")
    public ResponseEntity<Resource> downloadPublicFile(@PathVariable String id) {
        return fileStreamingHelper.stream(fileMetadataService.getPublicFileEntity(id), false);
    }

    /** Soft delete — moves the file to the recycle bin. */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFile(@PathVariable String id) {
        fileMetadataService.deleteFile(id);
        return ResponseEntity.noContent().build();
    }

    /** Restore a file from the recycle bin. */
    @PutMapping("/{id}/restore")
    public ResponseEntity<FileMetadataDTO> restoreFile(@PathVariable String id) {
        return ResponseEntity.ok(fileMetadataService.restoreFile(id));
    }

    /** Permanently delete a single file (disk + DB + share links). */
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<?> permanentlyDeleteFile(@PathVariable String id) {
        fileMetadataService.permanentlyDeleteFile(id);
        return ResponseEntity.noContent().build();
    }

    /** Permanently delete everything in the recycle bin. */
    @DeleteMapping("/trash/empty")
    public ResponseEntity<?> emptyTrash() {
        int removed = fileMetadataService.emptyTrash();
        return ResponseEntity.ok(Map.of("removed", removed));
    }

    /** Bulk move-to-trash for the selected files. */
    @PostMapping("/bulk-delete")
    public ResponseEntity<?> bulkDelete(@RequestBody BulkFileRequest request) {
        fileMetadataService.bulkSoftDelete(request.getFileIds());
        return ResponseEntity.ok(Map.of("deleted", request.getFileIds() == null ? 0 : request.getFileIds().size()));
    }

    /** Bulk download the selected files as a single ZIP archive. */
    @PostMapping("/bulk-download")
    public ResponseEntity<StreamingResponseBody> bulkDownload(@RequestBody BulkFileRequest request) {
        List<FileMetadataDocument> files = fileMetadataService.getOwnedFileEntities(request.getFileIds());

        StreamingResponseBody body = outputStream -> {
            try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
                Set<String> usedNames = new HashSet<>();
                for (FileMetadataDocument file : files) {
                    java.nio.file.Path path = Paths.get(file.getFileLocation());
                    if (!Files.exists(path)) {
                        log.warn("Skipping missing file during bulk download: {}", file.getFileLocation());
                        continue;
                    }
                    zip.putNextEntry(new ZipEntry(uniqueEntryName(usedNames, file.getName())));
                    Files.copy(path, zip);
                    zip.closeEntry();
                }
            }
        };

        String zipName = "cloudshare-" + System.currentTimeMillis() + ".zip";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipName + "\"")
                .body(body);
    }

    /** Ensures every ZIP entry has a unique name (appends " (n)" before the extension on collisions). */
    private String uniqueEntryName(Set<String> used, String name) {
        String base = name != null ? name : "file";
        if (used.add(base)) return base;
        int dot = base.lastIndexOf('.');
        String stem = dot > 0 ? base.substring(0, dot) : base;
        String ext = dot > 0 ? base.substring(dot) : "";
        int i = 1;
        String candidate;
        do {
            candidate = stem + " (" + i++ + ")" + ext;
        } while (!used.add(candidate));
        return candidate;
    }

    @PatchMapping("/{id}/toggle-public")
    public ResponseEntity<?> togglePublic(@PathVariable String id) {
        FileMetadataDTO file = fileMetadataService.togglePublic(id);
        return ResponseEntity.ok(file);
    }

    /** Move a single file into a folder (folderId null/"root" = move to root). Owner only. */
    @PutMapping("/{id}/move")
    public ResponseEntity<FileMetadataDTO> moveFile(@PathVariable String id,
                                                    @RequestBody MoveFileRequest request) {
        return ResponseEntity.ok(fileMetadataService.moveFile(id, request.getFolderId()));
    }

    /** Bulk move: body contains fileIds + target folderId. Owner only. */
    @PutMapping("/move")
    public ResponseEntity<List<FileMetadataDTO>> moveFiles(@RequestBody MoveFileRequest request) {
        return ResponseEntity.ok(fileMetadataService.moveFiles(request.getFileIds(), request.getFolderId()));
    }

    /** Toggle the favorite (star) flag on a file. Owner only. */
    @PutMapping("/{id}/favorite")
    public ResponseEntity<FileMetadataDTO> toggleFavorite(@PathVariable String id) {
        return ResponseEntity.ok(fileMetadataService.toggleFavorite(id));
    }
}
