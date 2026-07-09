package org.example.quickcode.langgraph4j.node;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.example.quickcode.core.CodegenOutputPaths;
import org.example.quickcode.langgraph4j.ai.ImageIntentClassifierService;
import org.example.quickcode.langgraph4j.model.ImageIntentResult;
import org.example.quickcode.langgraph4j.model.enums.ImageCategoryEnum;
import org.example.quickcode.langgraph4j.model.enums.ImageCollectionMode;
import org.example.quickcode.langgraph4j.state.WorkflowContext;
import org.example.quickcode.model.enums.CodeGenTypeEnum;
import org.example.quickcode.utils.SpringContextUtil;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 增量修改场景：判断是否需要搜集图片，并设置搜集模式。
 */
@Slf4j
public class ImageIntentRouterNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            Long appId = context.getAppId();
            CodeGenTypeEnum generationType = context.getGenerationType();
            boolean incremental = CodegenOutputPaths.hasExistingOutput(appId, generationType);
            context.setIncrementalMode(incremental);

            if (!incremental) {
                context.setImageCollectionMode(ImageCollectionMode.FULL);
                log.info("首次生成，使用完整图片搜集, appId={}", appId);
            } else {
                resolveIncrementalImageMode(context);
            }

            context.setCurrentStep("图片意图判断");
            return WorkflowContext.saveContext(context);
        });
    }

    private static void resolveIncrementalImageMode(WorkflowContext context) {
        try {
            ImageIntentClassifierService classifier = SpringContextUtil.getBean(ImageIntentClassifierService.class);
            ImageIntentResult result = classifier.classify(context.getOriginalPrompt());
            if (result != null && result.shouldCollect() && StrUtil.isNotBlank(result.getImageQuery())) {
                context.setImageCollectionMode(ImageCollectionMode.TARGETED);
                context.setTargetedImageQuery(result.getImageQuery().trim());
                context.setTargetedImageType(resolveImageType(result.getImageType()));
                log.info("增量修改需更换图片, query={}, type={}",
                        context.getTargetedImageQuery(), context.getTargetedImageType());
            } else {
                context.setImageCollectionMode(ImageCollectionMode.SKIP);
                log.info("增量修改无需更换图片，跳过搜集, appId={}", context.getAppId());
            }
        } catch (Exception e) {
            log.warn("图片意图分类失败，默认跳过搜集, appId={}: {}", context.getAppId(), e.getMessage());
            context.setImageCollectionMode(ImageCollectionMode.SKIP);
        }
    }

    private static ImageCategoryEnum resolveImageType(String imageType) {
        if (StrUtil.isBlank(imageType)) {
            return ImageCategoryEnum.CONTENT;
        }
        ImageCategoryEnum category = ImageCategoryEnum.getEnumByValue(imageType.trim().toUpperCase());
        return category != null ? category : ImageCategoryEnum.CONTENT;
    }
}
