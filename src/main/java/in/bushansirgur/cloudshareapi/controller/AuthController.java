package in.bushansirgur.cloudshareapi.controller;

import in.bushansirgur.cloudshareapi.dto.AuthResponse;
import in.bushansirgur.cloudshareapi.dto.ForgotPasswordRequest;
import in.bushansirgur.cloudshareapi.dto.LoginRequest;
import in.bushansirgur.cloudshareapi.dto.MessageResponse;
import in.bushansirgur.cloudshareapi.dto.RefreshTokenRequest;
import in.bushansirgur.cloudshareapi.dto.RegisterRequest;
import in.bushansirgur.cloudshareapi.dto.ResetPasswordRequest;
import in.bushansirgur.cloudshareapi.dto.UserDTO;
import in.bushansirgur.cloudshareapi.security.RateLimiterService;
import in.bushansirgur.cloudshareapi.security.SecurityUtil;
import in.bushansirgur.cloudshareapi.service.AuthService;
import in.bushansirgur.cloudshareapi.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final RateLimiterService rateLimiter;
    private final SecurityUtil securityUtil;

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request,
                                                    HttpServletRequest http) {
        enforceRateLimit("register:" + clientIp(http), 5);
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest http) {
        enforceRateLimit("login:" + clientIp(http), 10);
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/verify")
    public ResponseEntity<MessageResponse> verify(@RequestParam String token) {
        return ResponseEntity.ok(authService.verifyEmail(token));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                                          HttpServletRequest http) {
        enforceRateLimit("forgot:" + clientIp(http), 5);
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        if (request != null && request.getRefreshToken() != null) {
            authService.logout(request.getRefreshToken());
        }
        return ResponseEntity.ok(MessageResponse.builder().message("Logged out").build());
    }

    @PostMapping("/logout-all")
    public ResponseEntity<MessageResponse> logoutAll() {
        authService.logoutAll(securityUtil.getCurrentUserId());
        return ResponseEntity.ok(MessageResponse.builder().message("Logged out of all devices").build());
    }

    @GetMapping("/me")
    public ResponseEntity<UserDTO> me() {
        return ResponseEntity.ok(UserDTO.from(userService.getCurrentUser()));
    }

    private void enforceRateLimit(String key, int maxPerMinute) {
        if (!rateLimiter.allow(key, maxPerMinute)) {
            throw new RateLimitExceededException("Too many requests. Please wait a minute and try again.");
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** Thrown when an IP exceeds the auth rate limit; mapped to HTTP 429. */
    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }
}
