package org.example.quickcode.core;

import org.example.quickcode.constant.AppConstant;
import org.example.quickcode.core.MultiFilePhasedStreamSupport.Phase;
import org.example.quickcode.model.enums.CodeGenTypeEnum;

import java.io.File;

/**
 * 代码输出目录与文件路径工具。
 */
public final class CodegenOutputPaths {

    private CodegenOutputPaths() {
    }

    public static File outputDir(Long appId, CodeGenTypeEnum type) {
        return new File(AppConstant.CODE_OUTPUT_ROOT_DIR, type.getValue() + "_" + appId);
    }

    /** 是否已有生成产物（用于判断增量修改） */
    public static boolean hasExistingOutput(Long appId, CodeGenTypeEnum type) {
        if (appId == null || type == null) {
            return false;
        }
        File dir = outputDir(appId, type);
        if (!dir.isDirectory()) {
            return false;
        }
        if (type == CodeGenTypeEnum.VUE_PROJECT) {
            return new File(dir, "index.html").exists() || new File(dir, "src").isDirectory();
        }
        return new File(dir, "index.html").exists();
    }

    public static String phaseFileName(Phase phase) {
        return switch (phase) {
            case HTML -> "index.html";
            case CSS -> "style.css";
            case JS -> "script.js";
        };
    }
}
