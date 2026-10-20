<template>
  <div class="page">
    <header class="site-header">
      <div class="header-inner">
        <div class="logo" @click="goHome">个人知识博客</div>
        <nav class="nav">
          <el-button text @click="goAdmin">管理后台</el-button>
        </nav>
      </div>
    </header>

    <main class="container">
      <div class="toolbar">
        <el-radio-group v-model="categoryId" @change="onCategoryChange">
          <el-radio-button :value="0">全部</el-radio-button>
          <el-radio-button v-for="c in categories" :key="c.id" :value="c.id">
            {{ c.name }}
          </el-radio-button>
        </el-radio-group>
      </div>

      <el-card v-if="loading" class="empty-card" shadow="never">
        <el-skeleton :rows="5" animated />
      </el-card>

      <template v-else>
        <el-card v-for="a in articles" :key="a.id" class="article-card" shadow="hover" @click="goDetail(a.id)">
          <div class="article-head">
            <el-tag v-if="a.isTop" type="danger" size="small" effect="plain">置顶</el-tag>
            <h2 class="title">{{ a.title }}</h2>
            <span class="meta">
              {{ a.categoryName || '未分类' }} · {{ formatDate(a.createdAt) }} · 阅读 {{ a.viewCount }}
            </span>
          </div>
          <p class="summary">{{ a.summary }}</p>
        </el-card>

        <el-empty v-if="!articles.length" description="暂无文章" />

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="pageNum"
            :page-size="pageSize"
            :total="total"
            layout="prev, pager, next, total"
            @current-change="load"
          />
        </div>
      </template>
    </main>

    <footer class="site-footer">© 2026 个人知识博客 · MVP 演示</footer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPublicArticles } from '../../api/articles'
import { getCategories } from '../../api/categories'

const router = useRouter()
const articles = ref([])
const categories = ref([])
const categoryId = ref(0)
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize }
    if (categoryId.value) params.categoryId = categoryId.value
    const data = await getPublicArticles(params)
    articles.value = data.list || []
    total.value = data.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function onCategoryChange() {
  pageNum.value = 1
  load()
}

function goDetail(id) {
  router.push({ name: 'ArticleDetail', params: { id } })
}

function goHome() {
  router.push('/')
}

function goAdmin() {
  router.push('/admin')
}

function formatDate(ts) {
  const d = new Date(ts)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

onMounted(async () => {
  try {
    categories.value = (await getCategories()) || []
  } catch (e) {
    /* 分类加载失败不阻塞列表 */
  }
  load()
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
  position: sticky;
  top: 0;
  z-index: 10;
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
  padding: 20px 16px;
  width: 100%;
}
.toolbar {
  margin-bottom: 16px;
}
.article-card {
  margin-bottom: 14px;
  cursor: pointer;
  transition: transform 0.15s;
}
.article-card:hover {
  transform: translateY(-2px);
}
.article-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.title {
  margin: 0;
  font-size: 18px;
  color: #303133;
  flex: 1;
  min-width: 200px;
}
.meta {
  color: #909399;
  font-size: 12px;
}
.summary {
  color: #606266;
  font-size: 14px;
  line-height: 1.6;
  margin: 10px 0 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.empty-card {
  border: none;
}
.pagination-wrap {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}
.site-footer {
  text-align: center;
  color: #909399;
  font-size: 12px;
  padding: 20px;
}
</style>
