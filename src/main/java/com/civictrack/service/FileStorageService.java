package com.civictrack.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    /** Validates and saves an image; returns the generated file name. */
    String store(MultipartFile file);

    Resource load(String filename);
}
