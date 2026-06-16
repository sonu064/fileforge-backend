package in.bushansirgur.cloudshareapi.dto;

import in.bushansirgur.cloudshareapi.document.UserDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Public-safe view of a user — never includes the password hash. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private String id;
    private String firstName;
    private String lastName;
    private String email;
    private String role;
    private Integer credits;
    private String plan;
    private String profileImage;
    private boolean emailVerified;
    private Instant createdAt;

    public static UserDTO from(UserDocument u) {
        return UserDTO.builder()
                .id(u.getId())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .email(u.getEmail())
                .role(u.getRole() == null ? "USER" : u.getRole().name())
                .credits(u.getCredits())
                .plan(u.getPlan())
                .profileImage(u.getProfileImage())
                .emailVerified(u.isEmailVerified())
                .createdAt(u.getCreatedAt())
                .build();
    }
}
