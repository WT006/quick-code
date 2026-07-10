package org.example.quickcode.core.scaffold;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.example.quickcode.core.CodegenOutputPaths;
import org.example.quickcode.exception.BusinessException;
import org.example.quickcode.exception.ErrorCode;
import org.example.quickcode.model.enums.CodeGenTypeEnum;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.util.List;

/**
 * Vue 项目脚手架初始化：首次生成前从 classpath 复制预置模板。
 */
@Slf4j
@Component
public class VueProjectScaffoldInitializer {

    private static final String SCAFFOLD_BASE = "vue-scaffold/";

    private static final List<String> SCAFFOLD_FILES = List.of(
            "package.json",
            "vite.config.js",
            "index.html",
            "src/main.js",
            "src/App.vue",
            "src/router/index.js",
            "src/pages/HomePage.vue",
            "src/components/NavBar.vue",
            "src/styles/global.css"
    );

    /**
     * 若项目目录尚无产物，则复制脚手架模板。
     *
     * @return 是否执行了初始化
     */
    public boolean initIfAbsent(Long appId) {
        if (appId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "appId 不能为空");
        }
        if (CodegenOutputPaths.hasExistingOutput(appId, CodeGenTypeEnum.VUE_PROJECT)) {
            log.info("Vue 项目目录已存在，跳过脚手架初始化, appId={}", appId);
            return false;
        }
        File targetDir = CodegenOutputPaths.outputDir(appId, CodeGenTypeEnum.VUE_PROJECT);
        copyScaffoldTo(targetDir);
        log.info("Vue 脚手架初始化完成, appId={}, path={}", appId, targetDir.getAbsolutePath());
        return true;
    }

    private void copyScaffoldTo(File targetDir) {
        ClassLoader classLoader = getClass().getClassLoader();
        for (String relativePath : SCAFFOLD_FILES) {
            String resourcePath = SCAFFOLD_BASE + relativePath;
            try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
                if (inputStream == null) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                            "脚手架资源不存在: " + resourcePath);
                }
                File destFile = new File(targetDir, relativePath);
                FileUtil.mkParentDirs(destFile);
                FileUtil.writeFromStream(inputStream, destFile);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        "复制脚手架文件失败: " + relativePath + ", " + e.getMessage());
            }
        }
    }
}
