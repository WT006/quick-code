package org.example.quickcode.langgraph4j.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.example.quickcode.langgraph4j.model.ImageResource;
import org.example.quickcode.langgraph4j.model.QualityResult;
import org.example.quickcode.langgraph4j.model.enums.ImageCategoryEnum;
import org.example.quickcode.langgraph4j.model.enums.ImageCollectionMode;
import org.example.quickcode.model.enums.CodeGenTypeEnum;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 工作流上下文 - 存储所有状态信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowContext implements Serializable {

    /**
     * WorkflowContext 在 MessagesState 中的存储key
     */
    public static final String WORKFLOW_CONTEXT_KEY = "workflowContext";

    /**
     * 当前执行步骤
     */
    private String currentStep;

    /**
     * 应用 ID
     */
    private Long appId;

    /**
     * 用户原始输入的提示词
     */
    private String originalPrompt;

    /**
     * 图片资源字符串
     */
    private String imageListStr;

    /**
     * 图片资源列表
     */
    private List<ImageResource> imageList;

    /**
     * 增强后的提示词
     */
    private String enhancedPrompt;

    /**
     * 代码生成类型
     */
    private CodeGenTypeEnum generationType;

    /**
     * 生成的代码目录
     */
    private String generatedCodeDir;

    /**
     * 构建成功的目录
     */
    private String buildResultDir;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 质量检查结果
     */
    private QualityResult qualityResult;

    /**
     * 代码质检失败后的重试次数
     */
    @Builder.Default
    private int qualityCheckRetryCount = 0;

    /**
     * 是否为增量修改（已有生成产物）
     */
    @Builder.Default
    private boolean incrementalMode = false;

    /**
     * 图片搜集模式
     */
    @Builder.Default
    private ImageCollectionMode imageCollectionMode = ImageCollectionMode.FULL;

    /**
     * 定向搜集时的搜索关键词
     */
    private String targetedImageQuery;

    /**
     * 定向搜集时的图片类型
     */
    private ImageCategoryEnum targetedImageType;

    @Serial
    private static final long serialVersionUID = 1L;

    // ========== 上下文操作方法 ==========

    /**
     * 从 MessagesState 中获取 WorkflowContext
     */
    public static WorkflowContext getContext(MessagesState<String> state) {
        return (WorkflowContext) state.data().get(WORKFLOW_CONTEXT_KEY);
    }

    /**
     * 将 WorkflowContext 保存到 MessagesState 中
     */
    public static Map<String, Object> saveContext(WorkflowContext context) {
        return Map.of(WORKFLOW_CONTEXT_KEY, context);
    }
}
