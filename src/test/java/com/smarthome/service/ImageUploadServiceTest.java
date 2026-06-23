package com.smarthome.service;

import com.smarthome.dto.Dto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ImageUploadServiceTest {

    @InjectMocks ImageUploadService imageUploadService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(imageUploadService, "bucket", "my-bucket");
        ReflectionTestUtils.setField(imageUploadService, "publicBaseUrl", "https://cdn.example.com");
    }

    @Test
    void presign_returnsUploadAndPublicUrls() {
        Dto.ImageUploadRequest req = new Dto.ImageUploadRequest();
        req.setFilename("photo.jpg");

        Dto.ImageUploadResponse response = imageUploadService.presign(req);

        assertNotNull(response.getUploadUrl());
        assertTrue(response.getUploadUrl().contains("my-bucket"));
        assertNotNull(response.getPublicUrl());
        assertTrue(response.getPublicUrl().startsWith("https://cdn.example.com/products/"));
    }
}
