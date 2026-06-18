package com.whxbill.backend.modules.bill.controller;

import com.whxbill.backend.common.api.ApiResponse;
import com.whxbill.backend.modules.bill.service.FileStorageService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping("/upload")
    @PreAuthorize("hasAuthority('bill:create')")
    public ApiResponse<?> upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam(required = false) Long billId) {
        return ApiResponse.success(fileStorageService.upload(file, billId));
    }

    @GetMapping("/preview-url")
    @PreAuthorize("hasAuthority('bill:list')")
    public ApiResponse<String> previewUrl(@RequestParam("fileUrl") String fileUrl) {
        return ApiResponse.success(fileStorageService.resolveFileUrl(fileUrl));
    }

    @GetMapping("/content")
    @PreAuthorize("hasAuthority('bill:list')")
    public ResponseEntity<byte[]> content(@RequestParam("fileUrl") String fileUrl) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(fileStorageService.contentType(fileUrl)))
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(fileStorageService.download(fileUrl));
    }
}
