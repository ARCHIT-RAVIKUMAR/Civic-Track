package com.civictrack.service.impl;

import com.civictrack.exception.BusinessRuleException;
import com.civictrack.exception.ResourceNotFoundException;
import com.civictrack.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * OPTIONAL permanent storage (civictrack.storage.mode=supabase).
 * Photos go to a PRIVATE Supabase Storage bucket. They are never public: the app downloads them
 * with its server key and only serves them to users who may view the complaint.
 * Needs: SUPABASE_URL, SUPABASE_KEY (server secret / service key - NEVER commit it), SUPABASE_BUCKET.
 */
@Service
@ConditionalOnProperty(name = "civictrack.storage.mode", havingValue = "supabase")
public class SupabaseFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(SupabaseFileStorageService.class);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String baseUrl;
    private final String key;
    private final String bucket;

    public SupabaseFileStorageService(@Value("${civictrack.storage.supabase-url:}") String url,
                                      @Value("${civictrack.storage.supabase-key:}") String key,
                                      @Value("${civictrack.storage.bucket:complaint-images}") String bucket) {
        if (url.isBlank() || key.isBlank()) {
            throw new IllegalStateException(
                    "STORAGE_MODE=supabase needs SUPABASE_URL and SUPABASE_KEY to be set.");
        }
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.key = key;
        this.bucket = bucket;
    }

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("The image file is empty.");
        }
        if (file.getSize() > ImageFiles.MAX_BYTES) {
            throw new BusinessRuleException("The image is too large (maximum 2 MB).");
        }
        try {
            byte[] bytes = file.getBytes();
            String extension = ImageFiles.detectExtension(bytes, Math.min(bytes.length, 8));
            if (extension == null) {
                throw new BusinessRuleException("Only JPEG and PNG images are allowed.");
            }
            String filename = UUID.randomUUID() + "." + extension;
            HttpRequest request = HttpRequest.newBuilder(URI.create(objectUrl("", filename)))
                    .timeout(Duration.ofSeconds(30))
                    .header("apikey", key)
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", ImageFiles.contentTypeFor(extension))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.error("Supabase upload failed: HTTP {} {}", response.statusCode(), response.body());
                throw new BusinessRuleException("The image could not be saved. Please try again later.");
            }
            return filename;
        } catch (IOException ex) {
            log.error("Supabase upload failed", ex);
            throw new BusinessRuleException("The image could not be saved. Please try again later.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessRuleException("The image upload was interrupted. Please try again.");
        }
    }

    @Override
    public Resource load(String filename) {
        if (!ImageFiles.isSafeName(filename)) {
            throw new ResourceNotFoundException("Image not found.");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(objectUrl("authenticated/", filename)))
                    .timeout(Duration.ofSeconds(30))
                    .header("apikey", key)
                    .header("Authorization", "Bearer " + key)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                log.warn("Supabase download failed: HTTP {}", response.statusCode());
                throw new ResourceNotFoundException("Image not found.");
            }
            return new ByteArrayResource(response.body());
        } catch (IOException ex) {
            log.error("Supabase download failed", ex);
            throw new ResourceNotFoundException("Image not found.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResourceNotFoundException("Image not found.");
        }
    }

    private String objectUrl(String prefix, String filename) {
        return baseUrl + "/storage/v1/object/" + prefix + bucket + "/" + filename;
    }
}
