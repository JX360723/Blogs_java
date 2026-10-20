package com.xdl.blogscore.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 公共字段自动填充处理器
 * <p>
 * 为实体中标注 @TableField(fill = ...) 的 createdAt / updatedAt 赋值，
 * 落实 CLAUDE.md「自动注入 createdAt 和 updatedAt」的约定。
 *
 * @author xdl
 */
@Component
public class MybatisMetaObjectHandler implements MetaObjectHandler {

    private static final String CREATED_AT = "createdAt";

    private static final String UPDATED_AT = "updatedAt";

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, CREATED_AT, LocalDateTime.class, now);
        strictInsertFill(metaObject, UPDATED_AT, LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, UPDATED_AT, LocalDateTime.class, LocalDateTime.now());
    }
}
