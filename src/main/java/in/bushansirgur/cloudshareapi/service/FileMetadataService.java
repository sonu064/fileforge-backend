package in.bushansirgur.cloudshareapi.service;

import in.bushansirgur.cloudshareapi.document.FileMetadataDocument;
import in.bushansirgur.cloudshareapi.dto.FileMetadataDTO;
import in.bushansirgur.cloudshareapi.document.FolderDocument;
import in.bushansirgur.cloudshareapi.exceptions.FileAccessDeniedException;
import in.bushansirgur.cloudshareapi.exceptions.ResourceNotFoundException;
import in.bushansirgur.cloudshareapi.repository.FileMetadataRepository;
import in.bushansirgur.cloudshareapi.repository.FolderRepository;
import in.bushansirgur.cloudshareapi.repository.ShareLinkRepository;
import in.bushansirgur.cloudshareapi.security.SecurityUtil;
import in.bushansirgur.cloudshareapi.util.FileCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FileMetadataService {

    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final java.util.Set<String> BLOCKED_EXTENSIONS = java.util.Set.of(
            "exe", "bat", "cmd", "com", "msi", "scr", "ps1", "vbs", "js", "jar", "sh");

    private final SecurityUtil securityUtil;
    private final UserCreditsService userCreditsService;
    private final FileMetadataRepository fileMetadataRepository;
    private final FolderRepository folderRepository;
    private final ShareLinkRepository shareLinkRepository;

    private final Path uploadRoot;

    public FileMetadataService(SecurityUtil securityUtil,
                               UserCreditsService userCreditsService,
                               FileMetadataRepository fileMetadataRepository,
                               FolderRepository folderRepository,
                               ShareLinkRepository shareLinkRepository,
                               @Value("${app.upload-dir:upload}") String uploadDir) {
        this.securityUtil = securityUtil;
        this.userCreditsService = userCreditsService;
        this.fileMetadataRepository = fileMetadataRepository;
        this.folderRepository = folderRepository;
        this.shareLinkRepository = shareLinkRepository;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public List<FileMetadataDTO> uploadFiles(MultipartFile[] files) throws IOException {
        String userId = securityUtil.getCurrentUserId();
        log.info("Upload request received for userId={}", userId);

        List<FileMetadataDocument> savedFiles = new ArrayList<>();

        if (!userCreditsService.hasEnoughCredits(files.length)) {
            log.warn("Insufficient credits for userId={} — need {}, have {}",
                    userId, files.length, userCreditsService.getUserCredits().getCredits());
            throw new RuntimeException("Not enough credits to upload files. Please purchase more credits");
        }

        Path uploadPath = uploadRoot;
        Files.createDirectories(uploadPath);

        for (MultipartFile file : files) {
            validateUpload(file);
            String originalName = file.getOriginalFilename();
            String extension = StringUtils.getFilenameExtension(originalName);
            String fileName = extension != null && !extension.isBlank()
                    ? UUID.randomUUID() + "." + extension.toLowerCase()
                    : UUID.randomUUID().toString();
            Path targetLocation = uploadPath.resolve(fileName).normalize();
            if (!targetLocation.startsWith(uploadPath)) {
                throw new IllegalArgumentException("Invalid file name");
            }
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            FileMetadataDocument fileMetadata = FileMetadataDocument.builder()
                    .fileLocation(targetLocation.toString())
                    .name(file.getOriginalFilename())
                    .size(file.getSize())
                    .type(file.getContentType())
                    .userId(userId)
                    .isPublic(false)
                    .uploadedAt(LocalDateTime.now())
                    .build();

            userCreditsService.consumeCredit();

            savedFiles.add(fileMetadataRepository.save(fileMetadata));
            log.info("Saved metadata id={} for file={}", fileMetadata.getId(), originalName);
        }
        return savedFiles.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    private FileMetadataDTO mapToDTO(FileMetadataDocument fileMetadataDocument) {
        String id = fileMetadataDocument.getId();
        boolean isPublic = Boolean.TRUE.equals(fileMetadataDocument.getIsPublic());
        return FileMetadataDTO.builder()
                .id(id)
                .fileLocation(fileMetadataDocument.getFileLocation())
                .name(fileMetadataDocument.getName())
                .size(fileMetadataDocument.getSize())
                .type(fileMetadataDocument.getType())
                .userId(fileMetadataDocument.getUserId())
                .ownerId(fileMetadataDocument.getUserId())
                .isPublic(fileMetadataDocument.getIsPublic())
                .uploadedAt(fileMetadataDocument.getUploadedAt())
                .folderId(fileMetadataDocument.getFolderId())
                .favorite(fileMetadataDocument.isFavorite())
                .favoriteAt(fileMetadataDocument.getFavoriteAt())
                .tags(fileMetadataDocument.getTags())
                .deleted(fileMetadataDocument.isDeleted())
                .deletedAt(fileMetadataDocument.getDeletedAt())
                .category(FileCategory.classify(fileMetadataDocument.getName(), fileMetadataDocument.getType()))
                .viewUrl(isPublic ? "/files/public/" + id + "/view" : "/files/view/" + id)
                .downloadUrl(isPublic ? "/files/public/" + id + "/download" : "/files/download/" + id)
                .build();
    }

    /** Owner-checked single file metadata. */
    public FileMetadataDTO getOwnedFile(String id) {
        return mapToDTO(getOwnedFileEntity(id));
    }

    /**
     * Loads a file the current user owns. Throws 404 if it does not exist and 403 if it belongs
     * to a different user. Used by the authenticated view/download endpoints.
     */
    public FileMetadataDocument getOwnedFileEntity(String id) {
        String userId = securityUtil.getCurrentUserId();
        FileMetadataDocument file = fileMetadataRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
        if (!file.getUserId().equals(userId)) {
            log.warn("Access denied: userId={} attempted to access file {} owned by {}",
                    userId, id, file.getUserId());
            throw new FileAccessDeniedException("You do not have permission to access this file");
        }
        return file;
    }

    /**
     * Loads a file by id WITHOUT an ownership check. Only call this when access has already been
     * authorized by another mechanism (e.g. a valid share link).
     */
    public FileMetadataDocument getFileEntityByIdUnchecked(String id) {
        return fileMetadataRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
    }

    /** Loads a file that must be public (for unauthenticated share-link access). */
    public FileMetadataDocument getPublicFileEntity(String id) {
        FileMetadataDocument file = fileMetadataRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
        if (!Boolean.TRUE.equals(file.getIsPublic())) {
            throw new FileAccessDeniedException("This file is private");
        }
        return file;
    }

    public List<FileMetadataDTO> getFiles() {
        String userId = securityUtil.getCurrentUserId();
        return fileMetadataRepository.findByUserId(userId).stream()
                .map(this::mapToDTO).collect(Collectors.toList());
    }

    public FileMetadataDTO getPublicFile(String id) {
        return mapToDTO(getPublicFileEntity(id));
    }

    /** Soft delete: move the file to the recycle bin (kept on disk so it can be restored). */
    public void deleteFile(String id) {
        FileMetadataDocument file = getOwnedFileEntity(id);
        file.setDeleted(true);
        file.setDeletedAt(Instant.now());
        fileMetadataRepository.save(file);
        log.info("Moved file id={} to trash for userId={}", id, file.getUserId());
    }

    /** Restore a file from the recycle bin. */
    public FileMetadataDTO restoreFile(String id) {
        FileMetadataDocument file = getOwnedFileEntity(id);
        file.setDeleted(false);
        file.setDeletedAt(null);
        fileMetadataRepository.save(file);
        log.info("Restored file id={} for userId={}", id, file.getUserId());
        return mapToDTO(file);
    }

    /** Move several owned files to the recycle bin in one shot. */
    public void bulkSoftDelete(List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) return;
        Instant now = Instant.now();
        List<FileMetadataDocument> files = getOwnedFileEntities(fileIds);
        files.forEach(f -> { f.setDeleted(true); f.setDeletedAt(now); });
        fileMetadataRepository.saveAll(files);
        log.info("Moved {} file(s) to trash", files.size());
    }

    /** Permanently remove a file (disk + DB + its share links). Owner only. */
    public void permanentlyDeleteFile(String id) {
        FileMetadataDocument file = getOwnedFileEntity(id);
        removeFromDisk(file);
        shareLinkRepository.deleteByFileId(id);
        fileMetadataRepository.deleteById(id);
        log.info("Permanently deleted file id={} for userId={}", id, file.getUserId());
    }

    /** Permanently remove every file currently in the user's recycle bin. */
    public int emptyTrash() {
        String userId = securityUtil.getCurrentUserId();
        List<FileMetadataDocument> trashed = fileMetadataRepository.findByUserIdAndDeletedTrueOrderByDeletedAtDesc(userId);
        trashed.forEach(f -> {
            removeFromDisk(f);
            shareLinkRepository.deleteByFileId(f.getId());
        });
        fileMetadataRepository.deleteAll(trashed);
        log.info("Emptied trash: removed {} file(s) for userId={}", trashed.size(), userId);
        return trashed.size();
    }

    private void removeFromDisk(FileMetadataDocument file) {
        try {
            Path path = Paths.get(file.getFileLocation()).toAbsolutePath().normalize();
            if (!path.startsWith(uploadRoot)) {
                log.warn("Refusing to delete file outside upload root: {}", file.getId());
                return;
            }
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.error("Failed to delete file from disk for id={}", file.getId(), e);
        }
    }

    /** Loads several files the current user owns, skipping any that are missing/foreign. */
    public List<FileMetadataDocument> getOwnedFileEntities(List<String> ids) {
        String userId = securityUtil.getCurrentUserId();
        List<FileMetadataDocument> result = new ArrayList<>();
        for (String id : ids) {
            fileMetadataRepository.findById(id)
                    .filter(f -> f.getUserId().equals(userId))
                    .ifPresent(result::add);
        }
        if (result.isEmpty()) {
            throw new ResourceNotFoundException("No accessible files were found for the request");
        }
        return result;
    }

    public FileMetadataDTO togglePublic(String id) {
        FileMetadataDocument file = getOwnedFileEntity(id);
        file.setIsPublic(!Boolean.TRUE.equals(file.getIsPublic()));
        fileMetadataRepository.save(file);
        return mapToDTO(file);
    }

    /* ------------------------------ Folder + filters ------------------------------ */

    public List<FileMetadataDTO> getFiles(String folderId, String type, boolean favorite) {
        String userId = securityUtil.getCurrentUserId();

        List<FileMetadataDocument> files;
        if (folderId == null || folderId.isBlank()) {
            files = fileMetadataRepository.findByUserIdAndDeletedFalse(userId);
        } else if ("root".equalsIgnoreCase(folderId)) {
            files = fileMetadataRepository.findByUserIdAndFolderIdAndDeletedFalse(userId, null);
        } else {
            files = fileMetadataRepository.findByUserIdAndFolderIdAndDeletedFalse(userId, folderId);
        }

        return files.stream()
                .filter(f -> !favorite || f.isFavorite())
                .filter(f -> type == null || type.isBlank() || "all".equalsIgnoreCase(type)
                        || FileCategory.classify(f.getName(), f.getType()).equalsIgnoreCase(type))
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /** Files directly inside the given folder (null = root). No ownership check on the folder itself. */
    public List<FileMetadataDTO> getFilesInFolder(String folderId, String userId) {
        return fileMetadataRepository.findByUserIdAndFolderIdAndDeletedFalse(userId, folderId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<FileMetadataDTO> getFavorites() {
        String userId = securityUtil.getCurrentUserId();
        return fileMetadataRepository.findByUserIdAndFavoriteTrueAndDeletedFalse(userId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /** Files currently in the recycle bin (most recently deleted first). */
    public List<FileMetadataDTO> getTrash() {
        String userId = securityUtil.getCurrentUserId();
        return fileMetadataRepository.findByUserIdAndDeletedTrueOrderByDeletedAtDesc(userId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /** Moves a single owned file into the target folder (null = root). Validates folder ownership. */
    public FileMetadataDTO moveFile(String fileId, String folderId) {
        FileMetadataDocument file = getOwnedFileEntity(fileId);
        validateFolderOwnership(folderId, file.getUserId());
        file.setFolderId(normalizeFolderId(folderId));
        fileMetadataRepository.save(file);
        log.info("Moved file id={} to folderId={}", fileId, file.getFolderId());
        return mapToDTO(file);
    }

    /** Bulk move: moves every owned file in the list into the target folder. */
    public List<FileMetadataDTO> moveFiles(List<String> fileIds, String folderId) {
        List<FileMetadataDTO> moved = new ArrayList<>();
        for (String id : fileIds) {
            moved.add(moveFile(id, folderId));
        }
        return moved;
    }

    public FileMetadataDTO toggleFavorite(String id) {
        FileMetadataDocument file = getOwnedFileEntity(id);
        boolean next = !file.isFavorite();
        file.setFavorite(next);
        file.setFavoriteAt(next ? Instant.now() : null);
        fileMetadataRepository.save(file);
        return mapToDTO(file);
    }

    /** Searches the current user's files by name, tag or category (case-insensitive substring). */
    public List<FileMetadataDTO> searchFiles(String query) {
        String userId = securityUtil.getCurrentUserId();
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.isEmpty()) return List.of();
        return fileMetadataRepository.findByUserIdAndDeletedFalse(userId).stream()
                .filter(f -> matchesQuery(f, q))
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    private boolean matchesQuery(FileMetadataDocument f, String q) {
        if (f.getName() != null && f.getName().toLowerCase().contains(q)) return true;
        if (FileCategory.classify(f.getName(), f.getType()).contains(q)) return true;
        if (f.getType() != null && f.getType().toLowerCase().contains(q)) return true;
        if (f.getTags() != null) {
            return f.getTags().stream().anyMatch(t -> t != null && t.toLowerCase().contains(q));
        }
        return false;
    }

    /** Hard-deletes all files inside the given folder ids (disk + db). Used by folder cascade delete. */
    public void deleteFilesInFolders(List<String> folderIds, String userId) {
        for (String folderId : folderIds) {
            List<FileMetadataDocument> files = fileMetadataRepository.findByUserIdAndFolderId(userId, folderId);
            for (FileMetadataDocument file : files) {
                try {
                    Files.deleteIfExists(Paths.get(file.getFileLocation()));
                } catch (IOException e) {
                    log.error("Failed to delete file from disk during folder delete: {}", file.getFileLocation(), e);
                }
            }
            fileMetadataRepository.deleteAll(files);
        }
    }

    private void validateFolderOwnership(String folderId, String userId) {
        String normalized = normalizeFolderId(folderId);
        if (normalized == null) return;
        FolderDocument folder = folderRepository.findById(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));
        if (!folder.getUserId().equals(userId)) {
            throw new FileAccessDeniedException("You do not have permission to use this folder");
        }
    }

    private String normalizeFolderId(String folderId) {
        if (folderId == null || folderId.isBlank() || "root".equalsIgnoreCase(folderId)) return null;
        return folderId;
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Empty files are not allowed");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("File exceeds the maximum allowed size of 5 MB");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("File name is required");
        }
        if (originalName.contains("..") || originalName.contains("/") || originalName.contains("\\")) {
            throw new IllegalArgumentException("Invalid file name");
        }
        String extension = StringUtils.getFilenameExtension(originalName);
        if (extension != null && BLOCKED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new IllegalArgumentException("This file type is not allowed");
        }
    }
}
