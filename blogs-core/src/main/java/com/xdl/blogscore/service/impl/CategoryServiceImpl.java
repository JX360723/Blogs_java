package com.xdl.blogscore.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xdl.blogscore.mapper.CategoryMapper;
import com.xdl.blogscore.service.CategoryService;
import com.xdl.model.entity.Category;
import org.springframework.stereotype.Service;

/**
 * 分类 Service 实现类
 *
 * @author xdl
 */
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {
}
