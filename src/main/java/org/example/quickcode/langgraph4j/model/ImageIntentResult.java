package org.example.quickcode.langgraph4j.model;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 增量修改场景下，AI 对用户图片意图的分类结果。
 */
@Data
public class ImageIntentResult implements Serializable {

    /**
     * 是否需要搜集图片。false 表示跳过图片搜集。
     */
    private Boolean collect;

    /**
     * 搜索关键词或描述（collect=true 时必填）
     */
    private String imageQuery;

    /**
     * 图片类型：CONTENT / ILLUSTRATION / LOGO / ARCHITECTURE，默认 CONTENT
     */
    private String imageType;

    @Serial
    private static final long serialVersionUID = 1L;

    public boolean shouldCollect() {
        return Boolean.TRUE.equals(collect);
    }
}
