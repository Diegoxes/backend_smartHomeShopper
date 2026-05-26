package com.smarthome.service;

import com.smarthome.dto.Dto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ImageUploadService {

    @Value("${app.s3.bucket:}")
    private String bucket;

    @Value("${app.s3.public-base-url:}")
    private String publicBaseUrl;

    public Dto.ImageUploadResponse presign(Dto.ImageUploadRequest req) {
        String key = "products/" + UUID.randomUUID() + "-" + req.getFilename();
        String publicUrl = (publicBaseUrl != null && !publicBaseUrl.isBlank())
                ? publicBaseUrl.replaceAll("/+$", "") + "/" + key
                : "https://placeholder.local/" + key;
        return Dto.ImageUploadResponse.builder()
                .uploadUrl("https://" + (bucket != null && !bucket.isBlank() ? bucket : "bucket") + ".s3.amazonaws.com/" + key)
                .publicUrl(publicUrl)
                .build();
    }
}
