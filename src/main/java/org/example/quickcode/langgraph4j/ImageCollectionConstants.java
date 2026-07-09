package org.example.quickcode.langgraph4j;

/**
 * 图片搜集数量限制
 */
public final class ImageCollectionConstants {

    private ImageCollectionConstants() {
    }

    /** 内容图搜索任务上限 */
    public static final int MAX_CONTENT_TASKS = 3;

    /** 插画搜索任务上限 */
    public static final int MAX_ILLUSTRATION_TASKS = 2;

    /** 架构图任务上限 */
    public static final int MAX_DIAGRAM_TASKS = 3;

    /** Logo 生成任务上限 */
    public static final int MAX_LOGO_TASKS = 1;

    /** 单次项目搜集图片总数上限 */
    public static final int MAX_TOTAL_IMAGES = 30;

    /** 单次 Pexels 搜索返回数量 */
    public static final int CONTENT_IMAGES_PER_TASK = 5;

    /** 单次 Undraw 搜索返回数量 */
    public static final int ILLUSTRATIONS_PER_TASK = 4;

    /** 定向搜集（增量换图）最多返回张数 */
    public static final int MAX_TARGETED_IMAGES = 2;
}
