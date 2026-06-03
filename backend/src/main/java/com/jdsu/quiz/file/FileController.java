package com.jdsu.quiz.file;

import com.jdsu.quiz.security.JwtService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/upload")
    public FileStorageService.FileUploadResponse upload(
            @AuthenticationPrincipal JwtService.JwtPrincipal principal,
            @RequestPart("file") MultipartFile file
    ) {
        return fileStorageService.upload(principal, file);
    }

    @GetMapping("/{id}/signed-url")
    public FileStorageService.FileUrlResponse signedUrl(
            @AuthenticationPrincipal JwtService.JwtPrincipal principal,
            @PathVariable long id
    ) {
        return fileStorageService.signedUrl(principal, id);
    }
}
