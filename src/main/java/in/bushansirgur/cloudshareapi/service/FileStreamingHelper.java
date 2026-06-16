package in.bushansirgur.cloudshareapi.service;

import in.bushansirgur.cloudshareapi.document.FileMetadataDocument;
import in.bushansirgur.cloudshareapi.exceptions.FileAccessDeniedException;
import in.bushansirgur.cloudshareapi.exceptions.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Shared helper that turns a stored file into a streaming HTTP response. */
@Component
public class FileStreamingHelper {

    private final Path uploadRoot;

    public FileStreamingHelper(@Value("${app.upload-dir:upload}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /**
     * @param inline true → Content-Disposition: inline (preview); false → attachment (download).
     */
    public ResponseEntity<Resource> stream(FileMetadataDocument file, boolean inline) {
        Path path = Paths.get(file.getFileLocation()).toAbsolutePath().normalize();
        if (!path.startsWith(uploadRoot)) {
            throw new FileAccessDeniedException("Invalid file path");
        }

        Resource resource;
        try {
            resource = new UrlResource(path.toUri());
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("File location is invalid");
        }

        if (!resource.exists() || !resource.isReadable()) {
            throw new ResourceNotFoundException("File content is missing or corrupted");
        }

        MediaType mediaType = resolveMediaType(file);
        String fileName = file.getName() != null ? file.getName() : "download";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = (inline ? "inline" : "attachment")
                + "; filename=\"" + fileName + "\"; filename*=UTF-8''" + encoded;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(resource);
    }

    private MediaType resolveMediaType(FileMetadataDocument file) {
        if (file.getType() != null && !file.getType().isBlank()) {
            try {
                return MediaType.parseMediaType(file.getType());
            } catch (Exception ignored) {
                // fall through
            }
        }
        return MediaTypeFactory.getMediaType(file.getName()).orElse(MediaType.APPLICATION_OCTET_STREAM);
    }
}
