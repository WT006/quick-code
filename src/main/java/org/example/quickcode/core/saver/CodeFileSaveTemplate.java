package org.example.quickcode.core.saver;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import org.example.quickcode.constant.AppConstant;
import org.example.quickcode.exception.BusinessException;
import org.example.quickcode.exception.ErrorCode;
import org.example.quickcode.model.enums.CodeGenTypeEnum;


import java.io.File;
import java.nio.charset.StandardCharsets;

public abstract class CodeFileSaveTemplate<T> {

    // 文件保存根目录
    protected static final String FILE_SAVE_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;


    public final File saveCode(T result,Long appId) {
        //校验参数
        validateInput(result);
        //构建唯一目录路径
        String builtUniqueDir = buildUniqueDir(appId);
        //保存文件
        saveFile(result, builtUniqueDir);
        //返回文件对象
        return new File(builtUniqueDir);
    }

    /*
     * 获取生成类型
     * @return
     */
    protected abstract CodeGenTypeEnum getCodeGenType() ;
    /*
     * 保存文件
     */
    protected abstract void saveFile(T result, String builtUniqueDir);

    /**
     * 校验参数
     */
    protected void validateInput(T result) {
        if (result == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存文件失败，参数为空");
        }
    }

    /**
     * 构建唯一目录路径：tmp/code_output/bizType_雪花ID
     */
    protected final String buildUniqueDir(Long appId) {
        if(appId == null){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用id不能为空");
        }
        String bizType = getCodeGenType().getValue();
        String uniqueDirName = StrUtil.format("{}_{}", bizType, appId);
        String dirPath = FILE_SAVE_ROOT_DIR + File.separator + uniqueDirName;
        FileUtil.mkdir(dirPath);
        return dirPath;
    }

    /**
     * 写入单个文件
     */
    protected static void writeToFile(String dirPath, String filename, String content) {
        if (StrUtil.isBlank(content)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件内容不能为空: " + filename);
        }
        String filePath = dirPath + File.separator + filename;
        FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
    }


}
