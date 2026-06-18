package com.whxbill.backend.modules.bill.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.PutObjectRequest;
import com.whxbill.backend.common.exception.BusinessException;
import com.whxbill.backend.config.properties.OssProperties;
import com.whxbill.backend.modules.bill.entity.BizAttachment;
import com.whxbill.backend.modules.bill.mapper.BizAttachmentMapper;
import com.whxbill.backend.modules.bill.service.FileStorageService;
import com.whxbill.backend.security.SecurityUtils;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class FileStorageServiceImpl implements FileStorageService {

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final String[] ALLOWED_CONTENT_TYPES = {
        "image/jpeg", "image/png", "image/webp", "image/gif",
        "application/pdf", "text/plain",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    };

    private final OssProperties ossProperties;
    private final BizAttachmentMapper bizAttachmentMapper;

    @Override
    public Map<String, Object> upload(MultipartFile file, Long billId) {
        validateUpload(file);
        String safeFileName = sanitizeFileName(file.getOriginalFilename());
        String objectKey = userUploadPrefix() + LocalDate.now() + "/" + UUID.randomUUID() + "-" + safeFileName;
        String fileUrl = ossProperties.getPublicHost() + "/" + objectKey;
        OSS ossClient = new OSSClientBuilder().build(
            normalizeEndpoint(ossProperties.getEndpoint()),
            ossProperties.getAccessKeyId(),
            ossProperties.getAccessKeySecret());
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(resolveContentType(file));
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                ossProperties.getBucketName(), objectKey, file.getInputStream(), metadata);
            ossClient.putObject(putObjectRequest);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            ossClient.shutdown();
        }
        // OSS 上传成功后同步落库，满足附件可审计、可关联账单的业务要求。
        BizAttachment attachment = new BizAttachment();
        attachment.setUserId(SecurityUtils.getUserId());
        attachment.setBillId(billId);
        attachment.setFileName(file.getOriginalFilename());
        attachment.setFileUrl(fileUrl);
        attachment.setContentType(resolveContentType(file));
        attachment.setFileSize(file.getSize());
        bizAttachmentMapper.insert(attachment);

        Map<String, Object> result = new HashMap<>();
        result.put("id", attachment.getId());
        result.put("attachmentId", attachment.getId());
        result.put("fileName", file.getOriginalFilename());
        result.put("fileUrl", fileUrl);
        result.put("contentType", attachment.getContentType());
        result.put("fileSize", attachment.getFileSize());
        return result;
    }

    @Override
    public String resolveFileUrl(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return fileUrl;
        }
        String objectKey = extractObjectKey(fileUrl);
        validateCurrentUserObjectKey(objectKey);
        if (!StringUtils.hasText(objectKey)) {
            return fileUrl;
        }
        OSS ossClient = new OSSClientBuilder().build(
            normalizeEndpoint(ossProperties.getEndpoint()),
            ossProperties.getAccessKeyId(),
            ossProperties.getAccessKeySecret());
        try {
            GeneratePresignedUrlRequest request =
                new GeneratePresignedUrlRequest(ossProperties.getBucketName(), objectKey, HttpMethod.GET);
            request.setExpiration(new Date(System.currentTimeMillis() + 60L * 60L * 1000L));
            return ossClient.generatePresignedUrl(request).toString();
        } finally {
            ossClient.shutdown();
        }
    }

    @Override
    public byte[] download(String fileUrl) {
        String objectKey = extractObjectKey(fileUrl);
        validateCurrentUserObjectKey(objectKey);
        if (!StringUtils.hasText(objectKey)) {
            return new byte[0];
        }
        OSS ossClient = new OSSClientBuilder().build(
            normalizeEndpoint(ossProperties.getEndpoint()),
            ossProperties.getAccessKeyId(),
            ossProperties.getAccessKeySecret());
        try (OSSObject ossObject = ossClient.getObject(ossProperties.getBucketName(), objectKey)) {
            return StreamUtils.copyToByteArray(ossObject.getObjectContent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            ossClient.shutdown();
        }
    }

    @Override
    public String contentType(String fileUrl) {
        String objectKey = extractObjectKey(fileUrl);
        validateCurrentUserObjectKey(objectKey);
        if (!StringUtils.hasText(objectKey)) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        OSS ossClient = new OSSClientBuilder().build(
            normalizeEndpoint(ossProperties.getEndpoint()),
            ossProperties.getAccessKeyId(),
            ossProperties.getAccessKeySecret());
        try {
            ObjectMetadata metadata = ossClient.getObjectMetadata(ossProperties.getBucketName(), objectKey);
            return StringUtils.hasText(metadata.getContentType())
                ? metadata.getContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        } finally {
            ossClient.shutdown();
        }
    }

    private String extractObjectKey(String fileUrl) {
        String normalizedHost = trimTrailingSlash(ossProperties.getPublicHost());
        String normalizedUrl = trimTrailingSlash(fileUrl);
        if (normalizedUrl.startsWith(normalizedHost + "/")) {
            return normalizedUrl.substring(normalizedHost.length() + 1);
        }
        try {
            URL url = new URL(fileUrl);
            return url.getPath().startsWith("/") ? url.getPath().substring(1) : url.getPath();
        } catch (MalformedURLException ignored) {
            return fileUrl;
        }
    }

    private String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String resolveContentType(MultipartFile file) {
        if (StringUtils.hasText(file.getContentType())) {
            return file.getContentType();
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("上传文件不能超过 20MB");
        }
        String fileName = file.getOriginalFilename();
        if (!StringUtils.hasText(fileName) || !sanitizeFileName(fileName).equals(fileName)) {
            throw new BusinessException("文件名不合法");
        }
        String contentType = resolveContentType(file).toLowerCase(Locale.ROOT);
        // 图片按课程要求收紧到 5MB，其余受通用 20MB 上限约束。
        if (contentType.startsWith("image/") && file.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessException("图片文件不能超过 5MB");
        }
        boolean allowed = Arrays.asList(ALLOWED_CONTENT_TYPES).contains(contentType);
        if (!allowed) {
            throw new BusinessException("不支持的文件类型");
        }
    }

    private String sanitizeFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "file";
        }
        return fileName.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
    }

    private String userUploadPrefix() {
        return "uploads/" + SecurityUtils.getUserId() + "/";
    }

    private void validateCurrentUserObjectKey(String objectKey) {
        if (!StringUtils.hasText(objectKey) || !objectKey.startsWith(userUploadPrefix())) {
            throw new BusinessException("无权访问该文件");
        }
    }

    private String normalizeEndpoint(String endpoint) {
        if (!StringUtils.hasText(endpoint)) {
            return endpoint;
        }
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            return endpoint;
        }
        return "https://" + endpoint;
    }
}
