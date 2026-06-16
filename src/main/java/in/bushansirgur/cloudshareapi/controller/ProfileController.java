package in.bushansirgur.cloudshareapi.controller;

import in.bushansirgur.cloudshareapi.dto.ChangePasswordRequest;
import in.bushansirgur.cloudshareapi.dto.MessageResponse;
import in.bushansirgur.cloudshareapi.dto.UpdateProfileRequest;
import in.bushansirgur.cloudshareapi.dto.UserDTO;
import in.bushansirgur.cloudshareapi.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<UserDTO> getProfile() {
        return ResponseEntity.ok(UserDTO.from(userService.getCurrentUser()));
    }

    @PutMapping
    public ResponseEntity<UserDTO> updateProfile(@RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(UserDTO.from(userService.updateProfile(request)));
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserDTO> uploadAvatar(@RequestPart("avatar") MultipartFile avatar) throws IOException {
        return ResponseEntity.ok(UserDTO.from(userService.uploadAvatar(avatar)));
    }

    @PutMapping("/password")
    public ResponseEntity<MessageResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return ResponseEntity.ok(MessageResponse.builder().message("Password changed successfully").build());
    }
}
