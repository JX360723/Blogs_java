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
        <!-- MVP 先用纯文本渲染 Markdown 原文；正式接入 markdown-it 后替换 -->
        <div class="markdown-body">{{ article.contentMd }}</div>
      </template>
      <el-empty v-else description="文章不存在或未发布" />
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPublicArticle } from '../../api/articles'

const route = useRoute()
const router = useRouter()
const article = ref(null)
const loading = ref(true)

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
.markdown-body {
  color: #303133;
  line-height: 1.8;
  font-size: 15px;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
