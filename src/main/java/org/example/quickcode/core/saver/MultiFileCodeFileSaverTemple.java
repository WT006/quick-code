package org.example.quickcode.core.saver;

import cn.hutool.core.util.StrUtil;
import org.example.quickcode.ai.model.MultiFileCodeResult;
import org.example.quickcode.exception.BusinessException;
import org.example.quickcode.exception.ErrorCode;
import org.example.quickcode.model.enums.CodeGenTypeEnum;


public class MultiFileCodeFileSaverTemple extends CodeFileSaveTemplate<MultiFileCodeResult>{
    @Override
    protected CodeGenTypeEnum getCodeGenType() {
        return CodeGenTypeEnum.MULTI_FILE;
    }

    @Override
    protected void saveFile(MultiFileCodeResult result, String builtUniqueDir) {
        writeToFile(builtUniqueDir, "index.html", result.getHtmlCode());
        if (StrUtil.isNotBlank(result.getCssCode())) {
            writeToFile(builtUniqueDir, "style.css", result.getCssCode());
        }
        if (StrUtil.isNotBlank(result.getJsCode())) {
            writeToFile(builtUniqueDir, "script.js", result.getJsCode());
        }
    }

    @Override
    protected void validateInput(MultiFileCodeResult result) {
        super.validateInput(result);
        // 至少要有 HTML 代码，CSS 和 JS 可以为空
        if (StrUtil.isBlank(result.getHtmlCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "HTML代码内容不能为空");
        }
    }
}
