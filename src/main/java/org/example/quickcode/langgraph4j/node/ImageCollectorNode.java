package org.example.quickcode.langgraph4j.node;

import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.example.quickcode.langgraph4j.ImageCollectionConstants;
import org.example.quickcode.langgraph4j.WorkflowExecutionHolder;
import org.example.quickcode.utils.SpringContextUtil;
import org.example.quickcode.langgraph4j.ai.ImageCollectionPlanService;
import org.example.quickcode.langgraph4j.model.ImageCollectionPlan;
import org.example.quickcode.langgraph4j.model.ImageResource;
import org.example.quickcode.langgraph4j.state.WorkflowContext;
import org.example.quickcode.langgraph4j.tools.ImageSearchTool;
import org.example.quickcode.langgraph4j.tools.LogoGeneratorTool;
import org.example.quickcode.langgraph4j.tools.MermaidDiagramTool;
import org.example.quickcode.langgraph4j.tools.UndrawIllustrationTool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class ImageCollectorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            Long appId = context.getAppId();
            log.info("执行节点: 图片收集");
            WorkflowExecutionHolder.emitChunk(appId, "正在分析需求并收集图片素材...\n");
            String originalPrompt = context.getOriginalPrompt();
            List<ImageResource> collectedImages = new ArrayList<>();

            try {
                ImageCollectionPlanService planService = SpringContextUtil.getBean(ImageCollectionPlanService.class);
                ImageCollectionPlan plan = planService.planImageCollection(originalPrompt);
                plan = limitPlan(plan);
                log.info("获取到图片收集计划，开始并发执行");

                List<CompletableFuture<List<ImageResource>>> futures = new ArrayList<>();
                if (plan.getContentImageTasks() != null) {
                    ImageSearchTool imageSearchTool = SpringContextUtil.getBean(ImageSearchTool.class);
                    for (ImageCollectionPlan.ImageSearchTask task : plan.getContentImageTasks()) {
                        futures.add(CompletableFuture.supplyAsync(() ->
                                imageSearchTool.searchContentImages(task.query())));
                    }
                }
                if (plan.getIllustrationTasks() != null) {
                    UndrawIllustrationTool illustrationTool = SpringContextUtil.getBean(UndrawIllustrationTool.class);
                    for (ImageCollectionPlan.IllustrationTask task : plan.getIllustrationTasks()) {
                        futures.add(CompletableFuture.supplyAsync(() ->
                                illustrationTool.searchIllustrations(task.query())));
                    }
                }
                if (plan.getDiagramTasks() != null) {
                    MermaidDiagramTool diagramTool = SpringContextUtil.getBean(MermaidDiagramTool.class);
                    for (ImageCollectionPlan.DiagramTask task : plan.getDiagramTasks()) {
                        futures.add(CompletableFuture.supplyAsync(() ->
                                diagramTool.generateMermaidDiagram(task.mermaidCode(), task.description())));
                    }
                }
                if (plan.getLogoTasks() != null) {
                    LogoGeneratorTool logoTool = SpringContextUtil.getBean(LogoGeneratorTool.class);
                    for (ImageCollectionPlan.LogoTask task : plan.getLogoTasks()) {
                        futures.add(CompletableFuture.supplyAsync(() ->
                                logoTool.generateLogos(task.description())));
                    }
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                for (CompletableFuture<List<ImageResource>> future : futures) {
                    List<ImageResource> images = future.get();
                    if (images != null) {
                        collectedImages.addAll(images);
                    }
                }
                if (collectedImages.size() > ImageCollectionConstants.MAX_TOTAL_IMAGES) {
                    log.warn("图片数量 {} 超过上限 {}，已截断", collectedImages.size(), ImageCollectionConstants.MAX_TOTAL_IMAGES);
                    collectedImages = new ArrayList<>(collectedImages.subList(0, ImageCollectionConstants.MAX_TOTAL_IMAGES));
                }
                log.info("并发图片收集完成，共收集到 {} 张图片", collectedImages.size());
                if (!collectedImages.isEmpty()) {
                    WorkflowExecutionHolder.emitChunk(appId,
                            String.format("已收集 %d 张图片素材，正在增强提示词...\n", collectedImages.size()));
                }
            } catch (Exception e) {
                log.error("图片收集失败: {}", e.getMessage(), e);
            }
            context.setCurrentStep("图片收集");
            context.setImageList(collectedImages);
            return WorkflowContext.saveContext(context);
        });
    }

    private static ImageCollectionPlan limitPlan(ImageCollectionPlan plan) {
        ImageCollectionPlan limited = new ImageCollectionPlan();
        limited.setContentImageTasks(limitList(plan.getContentImageTasks(), ImageCollectionConstants.MAX_CONTENT_TASKS));
        limited.setIllustrationTasks(limitList(plan.getIllustrationTasks(), ImageCollectionConstants.MAX_ILLUSTRATION_TASKS));
        limited.setDiagramTasks(limitList(plan.getDiagramTasks(), ImageCollectionConstants.MAX_DIAGRAM_TASKS));
        limited.setLogoTasks(limitList(plan.getLogoTasks(), ImageCollectionConstants.MAX_LOGO_TASKS));
        return limited;
    }

    private static <T> List<T> limitList(List<T> list, int maxSize) {
        if (list == null || list.isEmpty()) {
            return list;
        }
        if (list.size() <= maxSize) {
            return list;
        }
        return list.subList(0, maxSize);
    }
}
