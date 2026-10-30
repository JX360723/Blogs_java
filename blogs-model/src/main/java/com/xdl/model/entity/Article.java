package com.xdl.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文章实体类
 * <p>
 * 存储文章的 Markdown 原文与渲染后的 HTML 内容，
 * 预留 ai_summary 字段用于 AI 自动摘要。
 *
 * @author xdl
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("article")
public class Article implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 状态：草稿（仅管理端可见）
     */
    public static final Integer STATUS_DRAFT = 0;

    /**
     * 状态：已发布（公开可见）
     */
    public static final Integer STATUS_PUBLISHED = 1;

    /**
     * 状态：已下架（仅管理端可见）
     */
    public static final Integer STATUS_OFFLINE = 2;

    /**
     * 是否置顶：否
     */
    public static final Integer NOT_TOP = 0;

    /**
     * 是否置顶：是
     */
    public static final Integer TOP = 1;

    /**
     * 文章主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文章标题
     */
    private String title;

    /**
     * 文章摘要/简介
     */
    private String summary;

    /**
     * Markdown 格式正文内容
     */
    private String contentMd;

    /**
     * 渲染后的 HTML 正文内容
     */
    private String contentHtml;

    /**
     * AI 自动生成的摘要
     */
    private String aiSummary;

    /**
     * 所属分类ID
     */
    private Long categoryId;

    /**
     * 作者用户ID
     */
    private Long userId;

    /**
     * 文章状态（0-草稿，1-已发布，2-已下架）
     */
    private Integer status;

    /**
     * 封面图片URL
     */
    private String coverImage;

    /**
     * 浏览次数
     */
    private Integer viewCount;

    /**
     * 是否置顶（0-否，1-是）
     */
    private Integer isTop;

    /**
     * 创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记（0-未删除，1-已删除）
     */
    @TableLogic
    private Integer deleted;
}
