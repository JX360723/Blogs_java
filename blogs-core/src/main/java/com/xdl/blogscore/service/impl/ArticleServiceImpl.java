package com.xdl.blogscore.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xdl.blogscore.mapper.ArticleMapper;
import com.xdl.blogscore.service.ArticleService;
import com.xdl.model.entity.Article;
import org.springframework.stereotype.Service;

/**
 * 文章 Service 实现类
 *
 * @author xdl
 */
@Service
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {
}
