package com.xdl.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 标签实体类
 * <p>
 * 对应表 {@code tag}，用于给文章打多对多标签，供访客端按标签筛选。
 * <p>
 * <b>刻意不含 {@code deleted} 字段</b>：全局配置了 {@code logic-delete-field: deleted}，
 * 一旦实体带上该字段，{@code removeById} 会被改写成 {@code UPDATE tag SET deleted = 1}；
 * 而 {@code uk_tag_name} 是唯一索引，同名标签「删掉再建」就会撞唯一键报 500。
 * 因此标签采用物理删除（见 {@code TagServiceImpl#deleteTag}）。
 *
 * @author xdl
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tag")
public class Tag implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 标签ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 标签名称，全局唯一（uk_tag_name）
     */
    private String name;

    /**
     * 标签描述，可为 null
     */
    private String description;

    /**
     * 创建时间，插入时自动填充
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间，插入与更新时自动填充
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
