package org.example.quickcode.langgraph4j.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.quickcode.langgraph4j.model.ImageResource;
import org.example.quickcode.langgraph4j.model.enums.ImageCategoryEnum;
import org.example.quickcode.manager.CosManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class LogoGeneratorTool {

    @Resource
    private CosManager cosManager;

    @Value("${dashscope.api-key:}")
    private String dashScopeApiKey;

    @Value("${dashscope.image-model:wan2.2-t2i-flash}")
    private String imageModel;

    @Tool("根据描述生成 Logo 设计图片，用于网站品牌标识")
    public List<org.example.quickcode.langgraph4j.model.ImageResource> generateLogos(@P("Logo 设计描述，如名称、行业、风格等，尽量详细") String description) {
        List<ImageResource> logoList = new ArrayList<>();
        try {
            // 构建 Logo 设计提示词
            String logoPrompt = String.format("生成 Logo，Logo 中禁止包含任何文字！Logo 介绍：%s", description);
            ImageSynthesisParam param = ImageSynthesisParam.builder()
                    .apiKey(dashScopeApiKey)
                    .model(imageModel)
                    .prompt(logoPrompt)
                    .size("512*512")
                    .n(1) // 生成 1 张足够，因为 AI 不知道哪张最好
                    .build();
            ImageSynthesis imageSynthesis = new ImageSynthesis();
            ImageSynthesisResult result = imageSynthesis.call(param);
            if (result != null && result.getOutput() != null && result.getOutput().getResults() != null) {
                List<Map<String, String>> results = result.getOutput().getResults();
                for (Map<String, String> imageResult : results) {
                    String aliyunUrl = imageResult.get("url");
                    if (StrUtil.isNotBlank(aliyunUrl)) {
                        String cosUrl = uploadToCos(aliyunUrl);
                        if (cosUrl != null) {
                            logoList.add(ImageResource.builder()
                                    .category(ImageCategoryEnum.LOGO)
                                    .description(description)
                                    .url(cosUrl)
                                    .build());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("生成 Logo 失败: {}", e.getMessage(), e);
        }
        return logoList;
    }


    private String uploadToCos(String aliyunUrl) {
        File tempFile = null;
        try {
            String fileName = IdUtil.fastSimpleUUID() + ".png";
            String localPath = System.getProperty("java.io.tmpdir") + File.separator + fileName;
            tempFile = new File(localPath);

            log.info("开始下载阿里云图片: {}", aliyunUrl);
            HttpUtil.downloadFile(aliyunUrl, tempFile);

            String cosKey = "logos/" + fileName;
            String cosUrl = cosManager.uploadFile(cosKey, tempFile);

            log.info("Logo 上传到 COS 成功: {}", cosUrl);
            return cosUrl;
        } catch (Exception e) {
            log.error("上传图片到 COS 失败: {}", e.getMessage(), e);
            return null;
        } finally {
            if (tempFile != null && tempFile.exists()) {
                FileUtil.del(tempFile);
            }
        }
    }
}
