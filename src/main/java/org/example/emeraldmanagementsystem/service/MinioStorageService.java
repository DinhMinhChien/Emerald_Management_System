package org.example.emeraldmanagementsystem.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import org.example.emeraldmanagementsystem.config.MinioProperties;
import org.example.emeraldmanagementsystem.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MinioStorageService {
    private final MinioClient minioClient;
    private final MinioProperties properties;

    public String uploadIncidentImage(MultipartFile file, String extension) {
        LocalDate today = LocalDate.now();
        String objectName = "incidents/%d/%02d/%s.%s".formatted(
                today.getYear(),
                today.getMonthValue(),
                UUID.randomUUID(),
                extension
        );
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.getBucketName()).build()
            );
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(properties.getBucketName()).build()
                );
            }
            byte[] content = file.getBytes();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.getBucketName())
                            .object(objectName)
                            .stream(new ByteArrayInputStream(content), content.length, -1)
                            .contentType(fileContentType(extension))
                            .build()
            );
            return properties.getUrl().replaceAll("/$", "")
                    + "/" + properties.getBucketName()
                    + "/" + objectName;
        } catch (Exception exception) {
            throw new BusinessException(502, "STORAGE_ERROR");
        }
    }

    public void delete(String url) {
        if (url == null || !url.contains("/" + properties.getBucketName() + "/")) {
            return;
        }
        String prefix = properties.getBucketName() + "/";
        int index = url.indexOf(prefix);
        if (index < 0) {
            return;
        }
        String objectName = url.substring(index + prefix.length());
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(properties.getBucketName())
                            .object(objectName)
                            .build()
            );
        } catch (Exception ignored) {
        }
    }

    private String fileContentType(String extension) {
        return switch (extension) {
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }
}
