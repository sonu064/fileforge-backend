package in.bushansirgur.cloudshareapi.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String firstName;
    private String lastName;
    private String profileImage;
}
