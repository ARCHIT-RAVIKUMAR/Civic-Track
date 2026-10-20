package com.civictrack.service.impl;

import com.civictrack.exception.BusinessRuleException;
import com.civictrack.exception.ResourceNotFoundException;
import com.civictrack.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * OPTIONAL feature: stores JPEG/PNG images on local disk.
 * NOTE: on free hosts such as Render Free the disk is wiped on every restart/redeploy.
 */
@Service
public class LocalFileStorageService implements FileStorageService {

    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final Pattern SAFE_NAME = Pattern.compile("[a-f0-9\\-]{36}\\.(jpg|png)");

    private final Path root;

    public LocalFileStorageService(@Value("${civictrack.upload-dir}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("The image file is empty.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessRuleException("The image is too large (maximum 2 MB).");
        }
        try {
            byte[] header = new byte[8];
            int read;
            try (InputStream in = file.getInputStream()) {
                read = in.readNBytes(header, 0, header.length);
            }
            String extension = detectExtension(header, read);
            if (extension == null) {
                throw new BusinessRuleException("Only JPEG and PNG images are allowed.");
            }
            Files.createDirectories(root);
            String filename = UUID.randomUUID() + "." + extension; // never trust the original name
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, root.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            }
            return filename;
        } catch (IOException ex) {
            throw new BusinessRuleException("The image could not be saved. Please try again.");
        }
    }

    /** Checks the real file signature ("magic bytes"), not just the name the browser sent. */
    private String detectExtension(byte[] h, int length) {
        if (length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') {
            return "png";
        }
        return null;
    }

    @Override
    public Resource load(String filename) {
        if (filename == null || !SAFE_NAME.matcher(filename).matches()) {
            throw new ResourceNotFoundException("Image not found.");
        }
        Path path = root.resolve(filename).normalize();
        if (!path.startsWith(root) || !Files.exists(path)) {
            throw new ResourceNotFoundException("Image not found.");
        }
        return new PathResource(path);
    }
}
