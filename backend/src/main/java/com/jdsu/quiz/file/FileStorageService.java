package com.jdsu.quiz.file;

import com.jdsu.quiz.security.JwtService;
import com.jdsu.quiz.security.MessageDigestUtil;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class FileStorageService {
    private final JdbcTemplate jdbcTemplate;
    private final MinioClient minioClient;
    private final String bucket;

    public FileStorageService(
            JdbcTemplate jdbcTemplate,
            @Value("${app.minio.endpoint}") String endpoint,
            @Value("${app.minio.access-key}") String accessKey,
            @Value("${app.minio.secret-key}") String secretKey,
            @Value("${app.minio.bucket}") String bucket
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucket = bucket;
    }

    public FileUploadResponse upload(JwtService.JwtPrincipal principal, MultipartFile file) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required.");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required.");
        }
        try {
            ensureBucket();
            String original = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
            String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
            String objectKey = principal.userId() + "/" + UUID.randomUUID() + "-" + original.replaceAll("[^A-Za-z0-9._-]", "_");
            byte[] bytes = file.getBytes();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .contentType(contentType)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .build()
            );
            jdbcTemplate.update(
                    """
                    INSERT INTO uploaded_files
                      (user_id, bucket, object_key, original_file_name, content_type, size_bytes, checksum_sha256)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    principal.userId(),
                    bucket,
                    objectKey,
                    original,
                    contentType,
                    file.getSize(),
                    MessageDigestUtil.sha256(new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1))
            );
            Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            return new FileUploadResponse(id == null ? 0 : id, bucket, objectKey, original, contentType, file.getSize(), signedUrl(objectKey));
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "File storage is unavailable.");
        }
    }

    public FileUrlResponse signedUrl(JwtService.JwtPrincipal principal, long fileId) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required.");
        }
        try {
            StoredFile file = jdbcTemplate.queryForObject(
                    "SELECT bucket, object_key FROM uploaded_files WHERE id = ? AND user_id = ? LIMIT 1",
                    (rs, rowNum) -> new StoredFile(rs.getString("bucket"), rs.getString("object_key")),
                    fileId,
                    principal.userId()
            );
            return new FileUrlResponse(fileId, signedUrl(file.objectKey()));
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found.");
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Signed URL could not be generated.");
        }
    }

    private void ensureBucket() throws Exception {
        try {
            if (minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                return;
            }
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        } catch (Exception exception) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private String signedUrl(String objectKey) throws Exception {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucket)
                        .object(objectKey)
                        .expiry(15, TimeUnit.MINUTES)
                        .build()
        );
    }

    public record FileUploadResponse(long id, String bucket, String objectKey, String fileName, String contentType, long sizeBytes, String signedUrl) {
    }

    public record FileUrlResponse(long id, String signedUrl) {
    }

    private record StoredFile(String bucket, String objectKey) {
    }
}
