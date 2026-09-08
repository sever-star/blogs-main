package com.syt.blog.util;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.PresignOptions;
import com.aliyun.sdk.service.oss2.credentials.StaticCredentialsProvider;
import com.aliyun.sdk.service.oss2.models.DeleteObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectRequest;
import com.aliyun.sdk.service.oss2.models.PresignResult;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 阿里云 OSS 文件存储工具类
 * <p>
 * 负责图片及通用文件的上传、删除以及访问地址生成，
 * 供文章配图、用户头像、附件下载等存储场景使用。
 */
@Slf4j
@Component
public class OssUtil {

    /** 支持上传的图片扩展名白名单 */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");

    /** 默认图片存储目录 */
    private static final String DEFAULT_DIR = "photos";

    /** 默认文件存储目录 */
    private static final String DEFAULT_FILE_DIR = "files";

    /** 日期目录格式（yyyy/MM/dd） */
    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** 地域 ID（需与 Bucket 所在区域一致） */
    @Value("${oss.region}")
    private String region;

    /** AccessKey ID（阿里云 RAM 访问密钥） */
    @Value("${oss.access-key-id}")
    private String accessKeyId;

    /** AccessKey Secret（阿里云 RAM 访问密钥） */
    @Value("${oss.access-key-secret}")
    private String accessKeySecret;

    /** Bucket 名称 */
    @Value("${oss.bucket}")
    private String bucket;

    /** 公开访问 URL 前缀（Bucket 需开启公共读，结尾不带 /） */
    @Value("${oss.url-prefix}")
    private String urlPrefix;

    /** OSS 客户端（单例，应用生命周期内复用） */
    private OSSClient client;

    /**
     * 初始化 OSS 客户端
     */
    @PostConstruct
    public void init() {
        this.client = OSSClient.newBuilder()
                .credentialsProvider(new StaticCredentialsProvider(accessKeyId, accessKeySecret))
                .region(region)
                .build();
    }

    /**
     * 应用关闭时释放 OSS 客户端资源
     */
    @PreDestroy
    public void destroy() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("关闭 OSS 客户端失败", e);
            }
        }
    }

    // ==================== 上传图片 ====================

    /**
     * 上传图片文件到默认目录（photos）
     *
     * @param file 图片文件（MultipartFile）
     * @return 图片公开访问 URL
     */
    public String uploadImage(MultipartFile file) {
        return uploadImage(file, DEFAULT_DIR);
    }

    /**
     * 上传图片文件到指定目录
     *
     * @param file 图片文件（MultipartFile）
     * @param dir  存储目录（如 avatar、cover）
     * @return 图片公开访问 URL
     */
    public String uploadImage(MultipartFile file, String dir) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("图片文件不能为空");
        }
        // 获取图片原始文件名
        String originalFilename = file.getOriginalFilename();
        // 验证图片扩展名
        validateExtension(originalFilename);

        try (InputStream in = file.getInputStream()) {
            //getContentType获取mime类型.MIME类型是传递的数据类型
            return upload(in, file.getSize(), originalFilename, file.getContentType(), dir);
        } catch (IOException e) {
            throw new IllegalStateException("读取图片文件失败", e);
        }
    }

    /**
     * 上传图片字节数据到指定目录
     *
     * @param data             图片字节数据
     * @param originalFilename 原始文件名（用于识别扩展名）
     * @param dir              存储目录（如 avatar、cover）
     * @return 图片公开访问 URL
     */
    public String uploadImage(byte[] data, String originalFilename, String dir) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("图片数据不能为空");
        }
        validateExtension(originalFilename);
        return upload(BinaryData.fromBytes(data), originalFilename, null, dir);
    }

    // ==================== 上传文件 ====================

    /**
     * 上传文件到默认目录（files）
     *
     * @param file 文件（MultipartFile）
     * @return 文件公开访问 URL
     */
    public String uploadFile(MultipartFile file) {
        return uploadFile(file, DEFAULT_FILE_DIR);
    }

    /**
     * 上传文件到指定目录
     *
     * @param file 文件（MultipartFile）
     * @param dir  存储目录（如 attachment）
     * @return 文件公开访问 URL
     */
    public String uploadFile(MultipartFile file, String dir) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        try (InputStream in = file.getInputStream()) {
            return upload(in, file.getSize(), file.getOriginalFilename(), file.getContentType(), dir);
        } catch (IOException e) {
            throw new IllegalStateException("读取文件失败", e);
        }
    }

    /**
     * 上传文件字节数据到指定目录
     *
     * @param data             文件字节数据
     * @param originalFilename 原始文件名
     * @param dir              存储目录
     * @return 文件公开访问 URL
     */
    public String uploadFile(byte[] data, String originalFilename, String dir) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("文件数据不能为空");
        }
        return upload(BinaryData.fromBytes(data), originalFilename, null, dir);
    }

    /**
     * 上传文件输入流（核心方法）
     *
     * @param in               文件输入流
     * @param size             文件大小（字节）
     * @param originalFilename 原始文件名
     * @param contentType      MIME 类型（可为空，自动推断）
     * @param dir              存储目录
     * @return 公开访问 URL
     */
    private String upload(InputStream in, long size, String originalFilename, String contentType, String dir) {
        return upload(BinaryData.fromStream(in, size), originalFilename, contentType, dir);
    }

    /**
     * 上传 BinaryData（核心方法）
     *
     * @param data             BinaryData 数据
     * @param originalFilename 原始文件名
     * @param contentType      MIME 类型（可为空，自动推断）
     * @param dir              存储目录
     * @return 公开访问 URL
     */
    private String upload(BinaryData data, String originalFilename, String contentType, String dir) {
        // key就是文件特定路径
        String key = generateObjectKey(originalFilename, dir);
        client.putObject(PutObjectRequest.newBuilder()
                .bucket(bucket)
                .key(key)
                .body(data)//存储的数据
                .contentType(resolveContentType(contentType, originalFilename))
                .build());
        return getUrl(key);
    }

    // ==================== 删除文件 ====================

    /**
     * 删除文件（支持传入完整访问 URL 或对象 Key）
     *
     * @param urlOrKey 文件访问 URL 或对象 Key
     */
    public void deleteFile(String urlOrKey) {
        client.deleteObject(DeleteObjectRequest.newBuilder()
                .bucket(bucket)
                .key(extractKey(urlOrKey))
                .build());
    }

    /**
     * 删除图片（兼容图片语义，等价于 deleteFile）
     *
     * @param urlOrKey 图片访问 URL 或对象 Key
     */
    public void deleteImage(String urlOrKey) {
        deleteFile(urlOrKey);
    }

    // ==================== 获取访问地址 ====================

    /**
     * 根据对象 Key 拼接公开访问 URL（需 Bucket 开启公共读）
     *
     * @param key 对象 Key
     * @return 公开访问 URL
     */
    public String getUrl(String key) {
        return urlPrefix + "/" + key;
    }

    /**
     * 生成预签名访问 URL（Bucket 为私有时使用，带时效）
     *
     * @param key      对象 Key
     * @param duration 有效时长（如 Duration.ofMinutes(30)）
     * @return 预签名 URL
     */
    public String generatePresignedUrl(String key, Duration duration) {
        PresignResult result = client.presign(
                GetObjectRequest.newBuilder().bucket(bucket).key(key).build(),
                PresignOptions.newBuilder().expiration(duration).build());
        return result.url();
    }

    // ==================== 私有方法 ====================

    /**
     * 生成对象 Key：目录/日期/UUID.扩展名
     *
     * @param originalFilename 原始文件名
     * @param dir              存储目录
     * @return 对象 Key
     */
    private String generateObjectKey(String originalFilename, String dir) {
        String ext = getExtensionOrEmpty(originalFilename);
        String fileName = UUID.randomUUID().toString().replace("-", "") + (ext.isEmpty() ? "" : "." + ext);
        return dir + "/" + LocalDate.now().format(DATE_DIR) + "/" + fileName;
    }

    /**
     * 从访问 URL 或 Key 中提取对象 Key
     *
     * @param urlOrKey 图片访问 URL 或对象 Key
     * @return 对象 Key
     */
    private String extractKey(String urlOrKey) {
        if (urlOrKey != null && urlOrKey.startsWith(urlPrefix)) {
            return urlOrKey.substring(urlPrefix.length() + 1);
        }
        return urlOrKey;
    }

    /**
     * 校验文件扩展名是否在图片白名单内
     *
     * @param filename 文件名
     */
    private void validateExtension(String filename) {
        String ext = getExtension(filename);
        if (!IMAGE_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("不支持的图片格式：" + ext);
        }
    }

    /**
     * 获取文件扩展名（小写，不含点）
     *
     * @param filename 文件名
     * @return 扩展名
     */
    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new IllegalArgumentException("文件名不合法");
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 获取文件扩展名（小写，不含点；无扩展名或文件名为空时返回空字符串）
     *
     * @param filename 文件名
     * @return 扩展名或空字符串
     */
    private String getExtensionOrEmpty(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 解析文件 Content-Type（未指定时先按已知格式推断，再尝试 JDK 内置映射，最终兜底为通用二进制类型）
     *
     * @param contentType      原始 Content-Type
     * @param originalFilename 原始文件名
     * @return MIME 类型
     */
    private String resolveContentType(String contentType, String originalFilename) {
        if (contentType != null && !contentType.isBlank()) {
            return contentType;
        }
        String ext = getExtensionOrEmpty(originalFilename);
        if ("webp".equals(ext)) {
            return "image/webp";
        }
        String guessed = URLConnection.guessContentTypeFromName(originalFilename);
        return guessed != null ? guessed : "application/octet-stream";
    }
}
