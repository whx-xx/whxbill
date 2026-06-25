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

    /**
     * 上传文件到 OSS，并把文件元信息写入 biz_attachment。
     */
    @Override
    public Map<String, Object> upload(MultipartFile file, Long billId) {
        validateUpload(file);
        // 先清洗文件名，防止路径穿越字符或 Windows 非法字符进入 OSS key。
        String safeFileName = sanitizeFileName(file.getOriginalFilename());
        // OSS 对象路径按用户和日期分目录：uploads/{userId}/{date}/{uuid}-{filename}。
        String objectKey = userUploadPrefix() + LocalDate.now() + "/" + UUID.randomUUID() + "-" + safeFileName;
        String fileUrl = ossProperties.getPublicHost() + "/" + objectKey;
        OSS ossClient = new OSSClientBuilder().build(
            normalizeEndpoint(ossProperties.getEndpoint()),
            ossProperties.getAccessKeyId(),
            ossProperties.getAccessKeySecret());
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            // metadata 里保存文件大小和 MIME 类型，后续浏览器预览时会用到 contentType。
            metadata.setContentLength(file.getSize());
            metadata.setContentType(resolveContentType(file));
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                ossProperties.getBucketName(), objectKey, file.getInputStream(), metadata);
            // putObject 是真正把文件流写入阿里云 OSS 的动作。
            ossClient.putObject(putObjectRequest);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            // OSSClient 持有网络连接，用完一定关闭。
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
        // 返回给前端的数据既有附件 id，也有可保存到头像/账单附件中的 fileUrl。
        result.put("id", attachment.getId());
        result.put("attachmentId", attachment.getId());
        result.put("fileName", file.getOriginalFilename());
        result.put("fileUrl", fileUrl);
        result.put("contentType", attachment.getContentType());
        result.put("fileSize", attachment.getFileSize());
        return result;
    }

    /**
     * 生成 OSS 临时访问地址，适合私有 bucket 或需要限时授权的文件预览。
     */
    @Override
    public String resolveFileUrl(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return fileUrl;
        }
        String objectKey = extractObjectKey(fileUrl);
        // 访问文件前先校验路径前缀，确保用户只能访问 uploads/{自己的用户ID}/ 下的对象。
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
            // 预签名 URL 有效期 1 小时，过期后重新请求接口生成即可。
            request.setExpiration(new Date(System.currentTimeMillis() + 60L * 60L * 1000L));
            return ossClient.generatePresignedUrl(request).toString();
        } finally {
            ossClient.shutdown();
        }
    }

    /**
     * 后端代理下载文件内容，前端头像预览等场景可以走 /api/files/content。
     */
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
            // OSSObject 的输入流读成 byte[] 后直接通过 ResponseEntity 返回给浏览器。
            return StreamUtils.copyToByteArray(ossObject.getObjectContent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            ossClient.shutdown();
        }
    }

    /**
     * 获取 OSS 对象的 MIME 类型，保证浏览器能正确展示图片、PDF 等文件。
     */
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

    /**
     * 从完整 URL 或相对路径中提取 OSS objectKey。
     */
    private String extractObjectKey(String fileUrl) {
        String normalizedHost = trimTrailingSlash(ossProperties.getPublicHost());
        String normalizedUrl = trimTrailingSlash(fileUrl);
        if (normalizedUrl.startsWith(normalizedHost + "/")) {
            // 如果是 publicHost 开头的完整地址，截掉域名部分。
            return normalizedUrl.substring(normalizedHost.length() + 1);
        }
        try {
            URL url = new URL(fileUrl);
            // 如果传入的是其他完整 URL，就取路径部分作为 objectKey。
            return url.getPath().startsWith("/") ? url.getPath().substring(1) : url.getPath();
        } catch (MalformedURLException ignored) {
            // 如果本来就是 uploads/2/... 这样的相对路径，直接返回。
            return fileUrl;
        }
    }

    /**
     * 去掉结尾斜杠，避免拼接或截取 URL 时出现多一个 / 的问题。
     */
    private String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /**
     * 从 MultipartFile 中读取 MIME 类型，读不到时用二进制流兜底。
     */
    private String resolveContentType(MultipartFile file) {
        if (StringUtils.hasText(file.getContentType())) {
            return file.getContentType();
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    /**
     * 上传前的安全校验：空文件、大小、文件名、MIME 类型都在这里拦截。
     */
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

    /**
     * 清理文件名中的危险字符，防止它影响 OSS 路径结构。
     */
    private String sanitizeFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "file";
        }
        return fileName.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
    }

    /**
     * 当前用户的 OSS 目录前缀，所有上传和访问校验都围绕它进行。
     */
    private String userUploadPrefix() {
        return "uploads/" + SecurityUtils.getUserId() + "/";
    }

    /**
     * 文件访问权限校验：objectKey 必须位于当前登录用户自己的目录。
     */
    private void validateCurrentUserObjectKey(String objectKey) {
        if (!StringUtils.hasText(objectKey) || !objectKey.startsWith(userUploadPrefix())) {
            throw new BusinessException("无权访问该文件");
        }
    }

    /**
     * 兼容配置里 endpoint 带不带 https:// 两种写法。
     */
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
