package in.bushansirgur.cloudshareapi.service;

import in.bushansirgur.cloudshareapi.document.FolderDocument;
import in.bushansirgur.cloudshareapi.dto.CreateFolderRequest;
import in.bushansirgur.cloudshareapi.dto.FolderContentsDTO;
import in.bushansirgur.cloudshareapi.dto.FolderDTO;
import in.bushansirgur.cloudshareapi.exceptions.FileAccessDeniedException;
import in.bushansirgur.cloudshareapi.exceptions.ResourceNotFoundException;
import in.bushansirgur.cloudshareapi.repository.FolderRepository;
import in.bushansirgur.cloudshareapi.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FolderService {

    private final FolderRepository folderRepository;
    private final FileMetadataService fileMetadataService;
    private final SecurityUtil securityUtil;

    private String currentUserId() {
        return securityUtil.getCurrentUserId();
    }

    public FolderDTO createFolder(CreateFolderRequest request) {
        String userId = currentUserId();
        String name = request.getFolderName() == null ? "" : request.getFolderName().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Folder name is required");
        }

        String parentId = normalize(request.getParentFolderId());
        if (parentId != null) {
            getOwnedFolder(parentId, userId); // validates parent exists and is owned
        }

        Instant now = Instant.now();
        FolderDocument folder = FolderDocument.builder()
                .folderName(name)
                .userId(userId)
                .parentFolderId(parentId)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return toDTO(folderRepository.save(folder));
    }

    public FolderDTO renameFolder(String id, String newName) {
        String userId = currentUserId();
        String name = newName == null ? "" : newName.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Folder name is required");
        }
        FolderDocument folder = getOwnedFolder(id, userId);
        folder.setFolderName(name);
        folder.setUpdatedAt(Instant.now());
        return toDTO(folderRepository.save(folder));
    }

    public FolderDTO moveFolder(String id, String newParentId) {
        String userId = currentUserId();
        FolderDocument folder = getOwnedFolder(id, userId);
        String target = normalize(newParentId);

        if (target != null) {
            if (target.equals(id)) {
                throw new IllegalArgumentException("A folder cannot be moved into itself");
            }
            getOwnedFolder(target, userId); // target must exist and be owned
            if (descendantIds(id).contains(target)) {
                throw new IllegalArgumentException("A folder cannot be moved into one of its own subfolders");
            }
        }

        folder.setParentFolderId(target);
        folder.setUpdatedAt(Instant.now());
        return toDTO(folderRepository.save(folder));
    }

    /** Deletes a folder and recursively all of its subfolders and the files inside them. */
    public void deleteFolder(String id) {
        String userId = currentUserId();
        getOwnedFolder(id, userId); // ownership check on the root of the subtree

        List<String> subtree = descendantIds(id);
        subtree.add(id);

        fileMetadataService.deleteFilesInFolders(subtree, userId);
        folderRepository.deleteAllById(subtree);
        log.info("Deleted folder subtree {} ({} folders) for userId={}", id, subtree.size(), userId);
    }

    public List<FolderDTO> listFolders() {
        String userId = currentUserId();
        return folderRepository.findByUserIdOrderByFolderNameAsc(userId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public FolderContentsDTO getContents(String folderId) {
        String userId = currentUserId();
        String id = normalize(folderId);

        FolderDocument current = id == null ? null : getOwnedFolder(id, userId);

        List<FolderDTO> subfolders = folderRepository
                .findByUserIdAndParentFolderIdOrderByFolderNameAsc(userId, id).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return FolderContentsDTO.builder()
                .currentFolder(current == null ? null : toDTO(current))
                .breadcrumb(buildBreadcrumb(current, userId))
                .folders(subfolders)
                .files(fileMetadataService.getFilesInFolder(id, userId))
                .build();
    }

    /* ------------------------------ helpers ------------------------------ */

    private List<FolderDTO> buildBreadcrumb(FolderDocument current, String userId) {
        List<FolderDTO> trail = new ArrayList<>();
        FolderDocument node = current;
        int guard = 0;
        while (node != null && guard++ < 100) {
            trail.add(toDTO(node));
            String parentId = node.getParentFolderId();
            if (parentId == null) break;
            node = folderRepository.findById(parentId).orElse(null);
            if (node != null && !node.getUserId().equals(userId)) break;
        }
        Collections.reverse(trail);
        return trail;
    }

    /** All descendant folder ids (not including the given folder). */
    private List<String> descendantIds(String rootId) {
        List<String> result = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(rootId);
        int guard = 0;
        while (!queue.isEmpty() && guard++ < 10000) {
            String parent = queue.poll();
            for (FolderDocument child : folderRepository.findByParentFolderId(parent)) {
                result.add(child.getId());
                queue.add(child.getId());
            }
        }
        return result;
    }

    private FolderDocument getOwnedFolder(String id, String userId) {
        FolderDocument folder = folderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));
        if (!folder.getUserId().equals(userId)) {
            throw new FileAccessDeniedException("You do not have permission to access this folder");
        }
        return folder;
    }

    private String normalize(String folderId) {
        if (folderId == null || folderId.isBlank() || "root".equalsIgnoreCase(folderId)) return null;
        return folderId;
    }

    public FolderDTO toDTO(FolderDocument folder) {
        long subfolders = folderRepository.countByUserIdAndParentFolderId(
                folder.getUserId(), folder.getId());
        return FolderDTO.builder()
                .id(folder.getId())
                .folderName(folder.getFolderName())
                .userId(folder.getUserId())
                .parentFolderId(folder.getParentFolderId())
                .createdAt(folder.getCreatedAt())
                .updatedAt(folder.getUpdatedAt())
                .itemCount(subfolders)
                .build();
    }

    public List<FolderDTO> searchFolders(String query) {
        String userId = currentUserId();
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return List.of();
        return folderRepository.findByUserIdAndFolderNameContainingIgnoreCase(userId, q).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
}
