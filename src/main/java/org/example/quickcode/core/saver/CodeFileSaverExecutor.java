package org.example.quickcode.core.saver;


import org.example.quickcode.ai.model.HtmlCodeResult;
import org.example.quickcode.ai.model.MultiFileCodeResult;
import org.example.quickcode.model.enums.CodeGenTypeEnum;

import java.io.File;

public class CodeFileSaverExecutor {

    private static final HtmlCodeFileSaveTemplate htmlCodeFileSaveTemplate = new HtmlCodeFileSaveTemplate();
    private static final MultiFileCodeFileSaverTemple multiFileCodeFileSaverTemple = new MultiFileCodeFileSaverTemple();


    public static File executeParser(Object codeContent, CodeGenTypeEnum codeGenType, Long appId) {
        return switch (codeGenType) {
            case HTML -> htmlCodeFileSaveTemplate.saveCode((HtmlCodeResult) codeContent,appId);
            case MULTI_FILE -> multiFileCodeFileSaverTemple.saveCode((MultiFileCodeResult) codeContent,appId);
            default -> throw new RuntimeException("不支持的代码生成类型：" + codeGenType);
        };
    }
}
