package org.example.quickcode.service;

public interface ScreenshotService {

    /**
     * 生成网页截图并上传到COS
     * @param webUrl 网页URL
     * @return 截图上传的URL
     */
    String generateAndUploadScreenshot(String webUrl);
}
