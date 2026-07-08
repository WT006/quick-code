package org.example.quickcode.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.DeleteObjectRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.quickcode.config.CosClientConfig;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * COS对象存储管理器
 *
 * @author yupi
 */
@Component
@Slf4j
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    /**
     * 上传对象
     *
     * @param key  唯一键（文件绝对路径）
     * @param file 文件
     * @return 上传结果
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 上传文件到 COS 并返回访问 URL
     *
     * @param key  COS对象键（完整路径）
     * @param file 要上传的文件
     * @return 文件的访问URL，失败返回null
     */
    public String uploadFile(String key, File file) {
        // 上传文件
        PutObjectResult result = putObject(key, file);
        if (result != null) {
            // 构建访问URL
            String url = String.format("%s/%s", cosClientConfig.getHost(), key);
            log.info("文件上传COS成功: {} -> {}", file.getName(), url);
            return url;
        } else {
            log.error("文件上传COS失败，返回结果为空");
            return null;
        }
    }

    /**
     * 删除 COS 对象
     *
     * @param key 对象键（如 screenshot/2026/07/06/xxx.jpg）
     */
    public void deleteObject(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            DeleteObjectRequest deleteObjectRequest = new DeleteObjectRequest(cosClientConfig.getBucket(), key);
            cosClient.deleteObject(deleteObjectRequest);
            log.info("删除COS文件成功: {}", key);
        } catch (Exception e) {
            log.error("删除COS文件失败: {}", key, e);
        }
    }

    /**
     * 根据 COS 访问 URL 删除对象
     *
     * @param url COS 文件的完整访问 URL
     */
    public void deleteObjectByUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        String key = extractKeyFromUrl(url);
        if (key != null) {
            deleteObject(key);
        }
    }

    /**
     * 从 COS 访问 URL 中提取对象键
     * 例如 https://host/screenshot/xxx.jpg -> screenshot/xxx.jpg
     */
    private String extractKeyFromUrl(String url) {
        String host = cosClientConfig.getHost();
        if (url.startsWith(host)) {
            String key = url.substring(host.length());
            if (key.startsWith("/")) {
                key = key.substring(1);
            }
            return key;
        }
        log.warn("无法从 URL 中提取 COS 对象键: {}", url);
        return null;
    }
}
