package in.bushansirgur.cloudshareapi.service;

import in.bushansirgur.cloudshareapi.document.EmailVerificationToken;
import in.bushansirgur.cloudshareapi.document.PasswordResetToken;
import in.bushansirgur.cloudshareapi.document.RefreshToken;
import in.bushansirgur.cloudshareapi.document.Role;
import in.bushansirgur.cloudshareapi.document.UserDocument;
import in.bushansirgur.cloudshareapi.dto.AuthResponse;
import in.bushansirgur.cloudshareapi.dto.ForgotPasswordRequest;
import in.bushansirgur.cloudshareapi.dto.LoginRequest;
import in.bushansirgur.cloudshareapi.dto.MessageResponse;
import in.bushansirgur.cloudshareapi.dto.RegisterRequest;
import in.bushansirgur.cloudshareapi.dto.ResetPasswordRequest;
import in.bushansirgur.cloudshareapi.dto.UserDTO;
import in.bushansirgur.cloudshareapi.exceptions.ResourceNotFoundException;
import in.bushansirgur.cloudshareapi.repository.EmailVerificationTokenRepository;
import in.bushansirgur.cloudshareapi.repository.PasswordResetTokenRepository;
import in.bushansirgur.cloudshareapi.repository.RefreshTokenRepository;
import in.bushansirgur.cloudshareapi.repository.UserRepository;
import in.bushansirgur.cloudshareapi.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;

    private final long refreshExpirationMs;
    private final boolean requireEmailVerification;
    private final boolean devExposeTokens;

    public AuthService(UserRepository userRepository,
                       EmailVerificationTokenRepository verificationTokenRepository,
                       PasswordResetTokenRepository resetTokenRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       EmailService emailService,
                       @Value("${jwt.refresh-expiration-ms}") long refreshExpirationMs,
                       @Value("${app.auth.require-email-verification}") boolean requireEmailVerification,
                       @Value("${app.auth.dev-expose-tokens}") boolean devExposeTokens) {
        this.userRepository = userRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.refreshExpirationMs = refreshExpirationMs;
        this.requireEmailVerification = requireEmailVerification;
        this.devExposeTokens = devExposeTokens;
    }

    public MessageResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        UserDocument user = UserDocument.builder()
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName() == null ? "" : request.getLastName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .credits(5)
                .plan("BASIC")
                .emailVerified(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        user = userRepository.save(user);

        String token = UUID.randomUUID().toString();
        verificationTokenRepository.save(EmailVerificationToken.builder()
                .token(token)
                .userId(user.getId())
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build());
        emailService.sendVerificationEmail(user.getEmail(), token);

        log.info("Registered new user {} ({})", user.getEmail(), user.getId());
        String message = requireEmailVerification
                ? "Account created. Check your email to verify your account before signing in."
                : "Account created successfully. You can now sign in.";
        return MessageResponse.builder()
                .message(message)
                .devToken(devExposeTokens ? token : null)
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        UserDocument user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        if (requireEmailVerification && !user.isEmailVerified()) {
            throw new IllegalStateException("Please verify your email before signing in.");
        }

        return issueTokens(user);
    }

    public MessageResponse verifyEmail(String token) {
        EmailVerificationToken vt = verificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification link"));
        if (vt.getExpiresAt().isBefore(Instant.now())) {
            verificationTokenRepository.delete(vt);
            throw new IllegalArgumentException("This verification link has expired");
        }
        UserDocument user = userRepository.findById(vt.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User account not found"));
        user.setEmailVerified(true);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        verificationTokenRepository.delete(vt);
        return MessageResponse.builder().message("Email verified successfully. You can now sign in.").build();
    }

    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        // Generic response regardless of whether the email exists, to avoid account enumeration.
        MessageResponse.MessageResponseBuilder response = MessageResponse.builder()
                .message("If an account exists for that email, a password reset link has been sent.");

        userRepository.findByEmail(email).ifPresent(user -> {
            resetTokenRepository.deleteByUserId(user.getId());
            String token = UUID.randomUUID().toString();
            resetTokenRepository.save(PasswordResetToken.builder()
                    .token(token)
                    .userId(user.getId())
                    .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                    .used(false)
                    .build());
            emailService.sendPasswordResetEmail(user.getEmail(), token);
            if (devExposeTokens) {
                response.devToken(token);
            }
        });

        return response.build();
    }

    public MessageResponse resetPassword(ResetPasswordRequest request) {
        PasswordResetToken rt = resetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset link"));
        if (rt.isUsed() || rt.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("This reset link has expired. Request a new one.");
        }
        UserDocument user = userRepository.findById(rt.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User account not found"));
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        rt.setUsed(true);
        resetTokenRepository.save(rt);
        // Force re-login everywhere after a password reset.
        refreshTokenRepository.deleteByUserId(user.getId());
        return MessageResponse.builder().message("Password updated successfully. You can now sign in.").build();
    }

    public AuthResponse refresh(String refreshTokenValue) {
        RefreshToken rt = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        if (rt.isRevoked() || rt.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Refresh token expired. Please sign in again.");
        }
        UserDocument user = userRepository.findById(rt.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User account not found"));

        // Rotate the refresh token.
        rt.setRevoked(true);
        refreshTokenRepository.save(rt);
        return issueTokens(user);
    }

    public void logout(String refreshTokenValue) {
        refreshTokenRepository.findByToken(refreshTokenValue).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
    }

    public void logoutAll(String userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    private AuthResponse issueTokens(UserDocument user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = UUID.randomUUID().toString() + UUID.randomUUID();
        refreshTokenRepository.save(RefreshToken.builder()
                .token(refreshToken)
                .userId(user.getId())
                .expiresAt(Instant.now().plusMillis(refreshExpirationMs))
                .revoked(false)
                .createdAt(Instant.now())
                .build());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(UserDTO.from(user))
                .build();
    }
}
