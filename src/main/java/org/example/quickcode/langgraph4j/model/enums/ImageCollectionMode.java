package org.example.quickcode.langgraph4j.model.enums;

/**
 * 工作流图片搜集模式
 */
public enum ImageCollectionMode {

    /** 首次生成：完整搜集 */
    FULL,

    /** 增量修改且需要换图：仅搜集指定图片 */
    TARGETED,

    /** 增量修改且无需换图：跳过搜集 */
    SKIP
}
