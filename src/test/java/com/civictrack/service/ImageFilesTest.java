package com.civictrack.service;

import com.civictrack.service.impl.ImageFiles;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageFilesTest {

    @Test
    void recognisesJpegAndPngBySignature() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        assertEquals("jpg", ImageFiles.detectExtension(jpeg, 8));
        assertEquals("png", ImageFiles.detectExtension(png, 8));
    }

    @Test
    void rejectsOtherFilesEvenIfRenamed() {
        byte[] script = "<script>alert(1)</script>".getBytes();
        assertNull(ImageFiles.detectExtension(script, 8));
        assertNull(ImageFiles.detectExtension(new byte[0], 0));
    }

    @Test
    void onlyGeneratedFileNamesAreSafe() {
        assertTrue(ImageFiles.isSafeName("3f2b8c1e-1a2b-4c3d-8e9f-0a1b2c3d4e5f.jpg"));
        assertFalse(ImageFiles.isSafeName("../secret.jpg"));
        assertFalse(ImageFiles.isSafeName("photo.exe"));
        assertFalse(ImageFiles.isSafeName(null));
    }
}
