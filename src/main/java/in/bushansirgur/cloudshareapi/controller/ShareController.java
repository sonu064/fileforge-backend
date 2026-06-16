package in.bushansirgur.cloudshareapi.controller;

import in.bushansirgur.cloudshareapi.dto.CreateShareRequest;
import in.bushansirgur.cloudshareapi.dto.PublicShareDTO;
import in.bushansirgur.cloudshareapi.dto.ShareLinkDTO;
import in.bushansirgur.cloudshareapi.service.FileStreamingHelper;
import in.bushansirgur.cloudshareapi.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;
    private final FileStreamingHelper fileStreamingHelper;

    /** Create a share link for a file (owner only). */
    @PostMapping("/share/{fileId}")
    public ResponseEntity<ShareLinkDTO> createShare(@PathVariable String fileId,
                                                    @RequestBody(required = false) CreateShareRequest request) {
        CreateShareRequest body = request != null ? request : new CreateShareRequest();
        return ResponseEntity.ok(shareService.createShareLink(fileId, body));
    }

    /** Resolve a share link (public). Increments the view count. */
    @GetMapping("/share/{shareId}")
    public ResponseEntity<PublicShareDTO> resolveShare(@PathVariable String shareId) {
        return ResponseEntity.ok(shareService.resolveAndCount(shareId));
    }

    /** Verify a share-link password (public). Returns {"valid": true|false}. */
    @PostMapping("/share/{shareId}/verify")
    public ResponseEntity<Map<String, Boolean>> verifyShare(@PathVariable String shareId,
                                                            @RequestBody(required = false) Map<String, String> body) {
        String password = body == null ? null : body.get("password");
        boolean valid = shareService.verifyPassword(shareId, password);
        return ResponseEntity.ok(Map.of("valid", valid));
    }

    /** Stream a shared file inline for preview (public, link-authorized). */
    @GetMapping("/share/{shareId}/view")
    public ResponseEntity<Resource> viewShared(@PathVariable String shareId,
                                               @RequestParam(name = "pwd", required = false) String pwd) {
        return fileStreamingHelper.stream(shareService.resolveFileForStreaming(shareId, pwd), true);
    }

    /** Download a shared file (public, link-authorized). */
    @GetMapping("/share/{shareId}/download")
    public ResponseEntity<Resource> downloadShared(@PathVariable String shareId,
                                                   @RequestParam(name = "pwd", required = false) String pwd) {
        return fileStreamingHelper.stream(shareService.resolveFileForStreaming(shareId, pwd), false);
    }

    /** List the current user's share links for a file (owner only). */
    @GetMapping("/files/{fileId}/shares")
    public ResponseEntity<List<ShareLinkDTO>> listShares(@PathVariable String fileId) {
        return ResponseEntity.ok(shareService.listForFile(fileId));
    }

    /** Revoke a share link (owner only). */
    @DeleteMapping("/share/{shareId}")
    public ResponseEntity<?> revokeShare(@PathVariable String shareId) {
        shareService.revoke(shareId);
        return ResponseEntity.noContent().build();
    }
}
