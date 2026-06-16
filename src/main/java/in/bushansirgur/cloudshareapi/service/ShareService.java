package in.bushansirgur.cloudshareapi.service;

import in.bushansirgur.cloudshareapi.document.FileMetadataDocument;
import in.bushansirgur.cloudshareapi.document.ShareLinkDocument;
import in.bushansirgur.cloudshareapi.dto.CreateShareRequest;
import in.bushansirgur.cloudshareapi.dto.PublicShareDTO;
import in.bushansirgur.cloudshareapi.dto.ShareLinkDTO;
import in.bushansirgur.cloudshareapi.exceptions.FileAccessDeniedException;
import in.bushansirgur.cloudshareapi.exceptions.LinkExpiredException;
import in.bushansirgur.cloudshareapi.exceptions.ResourceNotFoundException;
import in.bushansirgur.cloudshareapi.repository.ShareLinkRepository;
import in.bushansirgur.cloudshareapi.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareService {

    private final ShareLinkRepository repo;
    private final FileMetadataService fileMetadataService;
    private final SecurityUtil securityUtil;
    private final PasswordEncoder passwordEncoder;

    /** Create a share link for a file the current user owns. */
    public ShareLinkDTO createShareLink(String fileId, CreateShareRequest request) {
        // Throws 403/404 if the caller is not the owner or the file is missing.
        FileMetadataDocument file = fileMetadataService.getOwnedFileEntity(fileId);

        String rawPassword = request.getPassword();
        boolean protect = rawPassword != null && !rawPassword.isBlank();

        ShareLinkDocument link = ShareLinkDocument.builder()
                .shareId(generateShareId())
                .fileId(file.getId())
                .ownerId(file.getUserId())
                .expiresAt(resolveExpiry(request))
                .active(true)
                .viewCount(0)
                .createdAt(Instant.now())
                .passwordProtected(protect)
                .sharePassword(protect ? passwordEncoder.encode(rawPassword.trim()) : null)
                .build();

        link = repo.save(link);
        log.info("Created share link {} for file {} (expires {}, protected={})",
                link.getShareId(), fileId, link.getExpiresAt(), protect);
        return toDTO(link, file.getName());
    }

    /** Returns true if the supplied password unlocks the (active) link. */
    public boolean verifyPassword(String shareId, String password) {
        ShareLinkDocument link = requireActiveLink(shareId);
        return checkPassword(link, password);
    }

    private boolean checkPassword(ShareLinkDocument link, String password) {
        if (!link.isPasswordProtected()) return true;
        if (password == null) return false;
        return passwordEncoder.matches(password, link.getSharePassword());
    }

    /** Public resolve: validate, increment the view count, and return public metadata. */
    public PublicShareDTO resolveAndCount(String shareId) {
        ShareLinkDocument link = requireActiveLink(shareId);
        link.setViewCount(link.getViewCount() + 1);
        repo.save(link);

        FileMetadataDocument file = requireLiveFile(link);
        return PublicShareDTO.builder()
                .shareId(shareId)
                .name(file.getName())
                .type(file.getType())
                .size(file.getSize())
                .uploadedAt(file.getUploadedAt())
                .viewCount(link.getViewCount())
                .expiresAt(link.getExpiresAt())
                .passwordProtected(link.isPasswordProtected())
                .viewUrl("/share/" + shareId + "/view")
                .downloadUrl("/share/" + shareId + "/download")
                .build();
    }

    /**
     * Resolve the underlying file for streaming/download via a valid share link.
     * Enforces the link password when the link is protected.
     */
    public FileMetadataDocument resolveFileForStreaming(String shareId, String password) {
        ShareLinkDocument link = requireActiveLink(shareId);
        if (!checkPassword(link, password)) {
            throw new FileAccessDeniedException("A valid password is required to access this file");
        }
        return requireLiveFile(link);
    }

    /** Loads the file behind a link, treating a trashed/missing file as a dead link. */
    private FileMetadataDocument requireLiveFile(ShareLinkDocument link) {
        FileMetadataDocument file = fileMetadataService.getFileEntityByIdUnchecked(link.getFileId());
        if (file.isDeleted()) {
            throw new LinkExpiredException("The shared file is no longer available");
        }
        return file;
    }

    /** List all share links the current user created for a given file. */
    public List<ShareLinkDTO> listForFile(String fileId) {
        FileMetadataDocument file = fileMetadataService.getOwnedFileEntity(fileId);
        return repo.findByFileIdOrderByCreatedAtDesc(fileId).stream()
                .map(link -> toDTO(link, file.getName()))
                .collect(Collectors.toList());
    }

    /** Revoke (deactivate) a share link the current user owns. */
    public void revoke(String shareId) {
        ShareLinkDocument link = repo.findByShareId(shareId)
                .orElseThrow(() -> new ResourceNotFoundException("Share link not found"));
        String currentUser = securityUtil.getCurrentUserId();
        if (!link.getOwnerId().equals(currentUser)) {
            throw new FileAccessDeniedException("You cannot revoke this share link");
        }
        link.setActive(false);
        repo.save(link);
        log.info("Revoked share link {}", shareId);
    }

    /** Deactivate links that have passed their expiry. Runs hourly. */
    @Scheduled(fixedRate = 3_600_000L)
    public void sweepExpiredLinks() {
        List<ShareLinkDocument> expired = repo.findByActiveTrueAndExpiresAtBefore(Instant.now());
        if (expired.isEmpty()) return;
        expired.forEach(l -> l.setActive(false));
        repo.saveAll(expired);
        log.info("Swept {} expired share link(s)", expired.size());
    }

    // ---- helpers ----

    private ShareLinkDocument requireActiveLink(String shareId) {
        ShareLinkDocument link = repo.findByShareId(shareId)
                .orElseThrow(() -> new ResourceNotFoundException("Share link not found"));
        if (!link.isActive()) {
            throw new LinkExpiredException("This share link is no longer active");
        }
        if (link.getExpiresAt() != null && Instant.now().isAfter(link.getExpiresAt())) {
            link.setActive(false);
            repo.save(link);
            throw new LinkExpiredException("This share link has expired");
        }
        return link;
    }

    private Instant resolveExpiry(CreateShareRequest request) {
        String raw = request.effectiveExpiry();
        String option = raw == null ? "never" : raw.trim().toLowerCase();
        Instant now = Instant.now();
        switch (option) {
            case "1h":
                return now.plus(1, ChronoUnit.HOURS);
            case "24h":
                return now.plus(24, ChronoUnit.HOURS);
            case "7d":
                return now.plus(7, ChronoUnit.DAYS);
            case "30d":
                return now.plus(30, ChronoUnit.DAYS);
            case "custom":
                if (request.getCustomExpiresAt() == null) {
                    throw new IllegalArgumentException("customExpiresAt is required when expiration=custom");
                }
                if (request.getCustomExpiresAt().isBefore(now)) {
                    throw new IllegalArgumentException("Expiry date must be in the future");
                }
                return request.getCustomExpiresAt();
            case "never":
            default:
                return null;
        }
    }

    private String generateShareId() {
        return UUID.randomUUID().toString().replace("-", "")
                + Long.toHexString(System.nanoTime() & 0xffffff);
    }

    private ShareLinkDTO toDTO(ShareLinkDocument link, String fileName) {
        boolean expired = link.getExpiresAt() != null && Instant.now().isAfter(link.getExpiresAt());
        return ShareLinkDTO.builder()
                .shareId(link.getShareId())
                .fileId(link.getFileId())
                .fileName(fileName)
                .expiresAt(link.getExpiresAt())
                .active(link.isActive() && !expired)
                .expired(expired)
                .passwordProtected(link.isPasswordProtected())
                .viewCount(link.getViewCount())
                .createdAt(link.getCreatedAt())
                .build();
    }
}
