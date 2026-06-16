package in.bushansirgur.cloudshareapi.service;

import in.bushansirgur.cloudshareapi.document.UserDocument;
import in.bushansirgur.cloudshareapi.dto.ChangePasswordRequest;
import in.bushansirgur.cloudshareapi.dto.UpdateProfileRequest;
import in.bushansirgur.cloudshareapi.exceptions.ResourceNotFoundException;
import in.bushansirgur.cloudshareapi.repository.UserRepository;
import in.bushansirgur.cloudshareapi.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Set<String> ALLOWED_AVATAR_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );
    private static final long MAX_AVATAR_BYTES = 2 * 1024 * 1024; // 2 MB

    private final UserRepository userRepository;
    private final SecurityUtil securityUtil;
    private final PasswordEncoder passwordEncoder;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    public UserDocument getCurrentUser() {
        String userId = securityUtil.getCurrentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User account not found"));
    }

    public UserDocument getById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User account not found"));
    }

    public UserDocument updateProfile(UpdateProfileRequest request) {
        UserDocument user = getCurrentUser();
        if (request.getFirstName() != null && !request.getFirstName().isBlank()) {
            user.setFirstName(request.getFirstName().trim());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName().trim());
        }
        if (request.getProfileImage() != null) {
            user.setProfileImage(request.getProfileImage().trim());
        }
        user.setUpdatedAt(Instant.now());
        return userRepository.save(user);
    }

    public void changePassword(ChangePasswordRequest request) {
        UserDocument user = getCurrentUser();
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    /** Saves a profile avatar under upload/avatars/ and stores a public URL on the user. */
    public UserDocument uploadAvatar(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Avatar file is required");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new IllegalArgumentException("Avatar must be 2 MB or smaller");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_AVATAR_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Avatar must be JPEG, PNG, WebP, or GIF");
        }

        UserDocument user = getCurrentUser();
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (ext == null || ext.isBlank()) {
            ext = switch (contentType) {
                case "image/png" -> "png";
                case "image/webp" -> "webp";
                case "image/gif" -> "gif";
                default -> "jpg";
            };
        }

        Path avatarDir = Paths.get("upload", "avatars").toAbsolutePath().normalize();
        Files.createDirectories(avatarDir);
        String fileName = user.getId() + "-" + UUID.randomUUID().toString().substring(0, 8) + "." + ext.toLowerCase();
        Path target = avatarDir.resolve(fileName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        String prefix = (contextPath == null || contextPath.isBlank()) ? "" : contextPath;
        user.setProfileImage(prefix + "/upload/avatars/" + fileName);
        user.setUpdatedAt(Instant.now());
        return userRepository.save(user);
    }
}
