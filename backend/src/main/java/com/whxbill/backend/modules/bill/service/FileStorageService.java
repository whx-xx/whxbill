package com.whxbill.backend.modules.bill.service;

import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    Map<String, Object> upload(MultipartFile file, Long billId);

    String resolveFileUrl(String fileUrl);

    byte[] download(String fileUrl);

    String contentType(String fileUrl);
}
