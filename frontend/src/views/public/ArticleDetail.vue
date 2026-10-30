<template>
  <div class="page">
    <header class="site-header">
      <div class="header-inner">
        <div class="logo" @click="router.push('/')">个人知识博客</div>
        <nav class="nav">
          <el-button text @click="router.push('/')">返回列表</el-button>
        </nav>
      </div>
    </header>

    <main class="container">
      <el-skeleton v-if="loading" :rows="8" animated />
      <template v-else-if="article">
        <h1 class="detail-title">{{ article.title }}</h1>
        <div class="detail-meta">
          {{ article.categoryName || '未分类' }} · {{ formatDate(article.createdAt) }} · 阅读
          {{ article.viewCount }}
        </div>
        <el-divider />
        <!-- Markdown 渲染（轻量自实现，见 utils/markdown.js；与编辑端预览一致） -->
        <div class="markdown-body" v-html="renderedMd"></div>
      </template>
      <el-empty v-else description="文章不存在或未发布" />
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPublicArticle } from '../../api/articles'
import { renderMarkdown } from '../../utils/markdown'

const route = useRoute()
const router = useRouter()
const article = ref(null)
const loading = ref(true)

const renderedMd = computed(() => renderMarkdown(article.value?.contentMd || ''))

function formatDate(ts) {
  const d = new Date(ts)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(async () => {
  try {
    article.value = await getPublicArticle(route.params.id)
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
    article.value = null
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.site-header {
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}
.header-inner {
  max-width: 860px;
  margin: 0 auto;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.logo {
  font-size: 20px;
  font-weight: 700;
  color: #409eff;
  cursor: pointer;
}
.container {
  flex: 1;
  max-width: 860px;
  margin: 0 auto;
  padding: 24px 16px;
  width: 100%;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}
.detail-title {
  margin: 0;
  font-size: 26px;
  color: #303133;
}
.detail-meta {
  margin-top: 10px;
  color: #909399;
  font-size: 13px;
}
/* Markdown 排版（与编辑端预览一致） */
.markdown-body {
  color: #303133;
  line-height: 1.8;
  font-size: 15px;
  word-break: break-word;
}
.markdown-body h1,
.markdown-body h2,
.markdown-body h3 {
  margin: 0.8em 0 0.4em;
  line-height: 1.4;
}
.markdown-body h1 {
  font-size: 22px;
}
.markdown-body h2 {
  font-size: 19px;
}
.markdown-body h3 {
  font-size: 17px;
}
.markdown-body p {
  margin: 0.5em 0;
}
.markdown-body ul,
.markdown-body ol {
  padding-left: 1.6em;
  margin: 0.5em 0;
}
.markdown-body blockquote {
  margin: 0.5em 0;
  padding: 6px 14px;
  border-left: 3px solid #409eff;
  background: #f5f7fa;
  color: #606266;
}
.markdown-body pre {
  background: #f6f8fa;
  border-radius: 4px;
  padding: 10px 14px;
  overflow: auto;
  margin: 0.5em 0;
}
.markdown-body pre code {
  background: transparent;
  padding: 0;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 13px;
}
.markdown-body code {
  background: #f6f8fa;
  padding: 1px 5px;
  border-radius: 3px;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 13px;
}
.markdown-body a {
  color: #409eff;
}
.markdown-body strong {
  font-weight: 600;
}
</style>
