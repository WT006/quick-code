package org.example.quickcode.core.saver;

import cn.hutool.core.util.StrUtil;
import org.example.quickcode.ai.model.HtmlCodeResult;
import org.example.quickcode.exception.BusinessException;
import org.example.quickcode.exception.ErrorCode;
import org.example.quickcode.model.enums.CodeGenTypeEnum;


public class HtmlCodeFileSaveTemplate extends CodeFileSaveTemplate<HtmlCodeResult>{
    @Override
    protected CodeGenTypeEnum getCodeGenType() {
        return CodeGenTypeEnum.HTML;
    }

    @Override
    protected void saveFile(HtmlCodeResult result, String builtUniqueDir) {
        writeToFile(builtUniqueDir, "index.html", result.getHtmlCode());
    }

    @Override
    protected void validateInput(HtmlCodeResult result) {
        super.validateInput(result);
        // HTML 代码不能为空
        if (StrUtil.isBlank(result.getHtmlCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "HTML代码内容不能为空");
        }
    }
}
