package in.bushansirgur.cloudshareapi.exceptions;

import in.bushansirgur.cloudshareapi.controller.AuthController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${app.security.expose-error-details:false}")
    private boolean exposeErrorDetails;

    private ResponseEntity<Map<String, Object>> buildError(HttpStatus status, String message) {
        Map<String, Object> data = new HashMap<>();
        data.put("status", status.value());
        data.put("message", message);
        return ResponseEntity.status(status).body(data);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<?> handleDuplicateEmailException(DuplicateKeyException ex) {
        log.warn("Duplicate key violation");
        return buildError(HttpStatus.CONFLICT, "A record with that value already exists");
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<?> handleUsernameNotFound(UsernameNotFoundException ex) {
        log.warn("User not authenticated: {}", ex.getMessage());
        return buildError(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(FileAccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(FileAccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return buildError(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(LinkExpiredException.class)
    public ResponseEntity<?> handleLinkExpired(LinkExpiredException ex) {
        log.info("Share link gone: {}", ex.getMessage());
        return buildError(HttpStatus.GONE, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldError() != null
                ? ex.getBindingResult().getFieldError().getDefaultMessage()
                : "Validation failed";
        return buildError(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<?> handleBadCredentials(BadCredentialsException ex) {
        return buildError(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException ex) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalState(IllegalStateException ex) {
        return buildError(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(AuthController.RateLimitExceededException.class)
    public ResponseEntity<?> handleRateLimit(AuthController.RateLimitExceededException ex) {
        return buildError(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<?> handleMissingPart(MissingServletRequestPartException ex) {
        log.warn("Missing multipart part: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST,
                "Missing file part 'files'. Do not set Content-Type manually on the client — let the browser add the multipart boundary.");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<?> handleMultipart(MultipartException ex) {
        log.error("Multipart parse error", ex);
        String message = exposeErrorDetails
                ? "Invalid multipart request: " + ex.getMostSpecificCause().getMessage()
                : "Invalid multipart request";
        return buildError(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("File too large: {}", ex.getMessage());
        return buildError(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the maximum allowed size of 5 MB.");
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<?> handleIOException(IOException ex) {
        log.error("I/O error during file operation", ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR,
                exposeErrorDetails ? "Failed to store file: " + ex.getMessage() : "Failed to store file");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntime(RuntimeException ex) {
        log.error("Unhandled runtime exception", ex);
        return buildError(HttpStatus.BAD_REQUEST,
                exposeErrorDetails ? ex.getMessage() : "Request could not be processed");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR,
                exposeErrorDetails ? ex.getMessage() : "An unexpected error occurred");
    }
}
