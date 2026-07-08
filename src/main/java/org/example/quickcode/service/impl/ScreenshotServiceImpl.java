package org.example.quickcode.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.quickcode.exception.ErrorCode;
import org.example.quickcode.exception.ThrowUtils;
import org.example.quickcode.manager.CosManager;
import org.example.quickcode.service.ScreenshotService;
import org.example.quickcode.utils.WebScreenshotUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@Slf4j
public class ScreenshotServiceImpl implements ScreenshotService {

    @Resource
    private CosManager cosManager;

    @Override
    public String generateAndUploadScreenshot(String webUrl) {
        ThrowUtils.throwIf(StrUtil.isBlank(webUrl), ErrorCode.PARAMS_ERROR, "webUrl不能为空");
        log.info("开始生成网页截图,URL : {}", webUrl);
        try {
            //1、生成网页截图
            String localScreenshotPath = WebScreenshotUtils.saveWebPageScreenshot(webUrl);
            ThrowUtils.throwIf(localScreenshotPath == null, ErrorCode.SYSTEM_ERROR, "网页截图生成失败");
            //2、上传到COS
            try {
                String screenshotUrl = uploadScreenshot(localScreenshotPath);
                ThrowUtils.throwIf(screenshotUrl == null, ErrorCode.SYSTEM_ERROR, "网页截图上传COS失败");
                log.info("网页截图上传COS成功,URL: {}", screenshotUrl);
                return screenshotUrl;
            }finally {
                cleanupLocalFile(localScreenshotPath);
            }
        } finally {
            // 3、释放当前线程的 WebDriver 浏览器进程
            WebScreenshotUtils.closeDriver();
        }
    }


    private String uploadScreenshot(String localScreenshotPath) {
        if(StrUtil.isBlank(localScreenshotPath)){
            return null;
        }
        File file = new File(localScreenshotPath);
        if(!file.exists()){
            log.error("文件不存在: {}", localScreenshotPath);
            return null;
        }
        //生成COS对象键
        String fileName = UUID.randomUUID().toString().substring(0, 8)+"_compressed.jpg";
        String cosKey=generateScreenshotKey(fileName);
        return cosManager.uploadFile(cosKey, file);
    }

    private String generateScreenshotKey(String fileName) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return String.format("screenshot/%s/%s", datePath, fileName);
    }


    private void cleanupLocalFile(String localFilePath){
        File file = new File(localFilePath);
        if(file.exists()){
            File parentFile = file.getParentFile();
            FileUtil.del(parentFile);
            log.info("删除临时文件: {}", parentFile.getAbsolutePath());
        }
    }
}
