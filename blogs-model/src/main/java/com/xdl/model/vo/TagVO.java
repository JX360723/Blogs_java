package com.xdl.model.vo;

import com.xdl.model.entity.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 标签视图对象
 * <p>
 * 公开接口与管理端共用：访客端拿它渲染标签筛选条，管理端拿它渲染标签列表，
 * 文章详情里的 {@code tags} 也是这个结构（前端可直接取 {@code tag.name} 展示）。
 * <p>
 * 刻意不暴露 {@code updatedAt}：页面上没有「标签更新时间」这类需求，
 * 少一个字段就少一处时间格式化的问题。
 *
 * @author xdl
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 标签ID
     */
    private Long id;

    /**
     * 标签名称
     */
    private String name;

    /**
     * 标签描述
     */
    private String description;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 由实体转换为视图对象
     *
     * @param tag 标签实体
     * @return 标签视图
     */
    public static TagVO from(Tag tag) {
        return TagVO.builder()
                .id(tag.getId())
                .name(tag.getName())
                .description(tag.getDescription())
                .createdAt(tag.getCreatedAt())
                .build();
    }
}
