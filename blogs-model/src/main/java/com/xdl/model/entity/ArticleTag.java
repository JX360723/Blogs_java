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
 * 文章标签关联实体类
 * <p>
 * 对应表 {@code article_tag}，是 article 与 tag 的多对多中间表。
 * 唯一约束 {@code uk_article_tag (article_id, tag_id)} 保证同一篇文章不会重复绑定同一个标签，
 * 因此写库前必须先按 tagId 去重，否则重复插入会抛 Duplicate entry。
 * <p>
 * 与 {@link Tag} 一样<b>不带 {@code deleted} 字段</b>，走物理删除：
 * 解绑标签本来就不需要保留历史，留着软删行反而会让唯一索引挡住「解绑后再绑回来」。
 *
 * @author xdl
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("article_tag")
public class ArticleTag implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文章ID
     */
    private Long articleId;

    /**
     * 标签ID
     */
    private Long tagId;

    /**
     * 绑定时间，插入时自动填充
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
