<template>
  <div class="page">
    <header class="site-header">
      <div class="header-inner">
        <div class="logo" @click="goHome">个人知识博客</div>
        <nav class="nav">
          <el-button text @click="goArticles">文章</el-button>
          <el-button text @click="goAdmin">管理后台</el-button>
        </nav>
      </div>
    </header>

    <main class="container">
      <el-card shadow="never" class="card">
        <h1 class="card-title">关于这个站点</h1>
        <p class="para">
          这里是我记录学习过程、技术实践与生活兴趣的地方。写作以「能被未来的自己看懂」为标准：
          尽量写清背景、踩过的坑和最后的结论，而不是只留一段能跑通的代码。
        </p>
        <p class="para">
          站点本身也是我练手的一个完整项目——前后端分离、自己写接口、自己部署，
          所以你在阅读时看到的每一次改版，背后大概都对应着一次重构。
        </p>
      </el-card>

      <el-card shadow="never" class="card">
        <h2 class="card-title">技术栈</h2>
        <div class="stack-group">
          <div class="stack-label">前端</div>
          <div class="stack-tags">
            <el-tag v-for="t in frontendStack" :key="t" effect="plain" size="small">{{ t }}</el-tag>
          </div>
        </div>
        <div class="stack-group">
          <div class="stack-label">后端</div>
          <div class="stack-tags">
            <el-tag v-for="t in backendStack" :key="t" effect="plain" size="small" type="success">{{ t }}</el-tag>
          </div>
        </div>
        <div class="stack-group">
          <div class="stack-label">部署</div>
          <div class="stack-tags">
            <el-tag v-for="t in deployStack" :key="t" effect="plain" size="small" type="warning">{{ t }}</el-tag>
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="card">
        <h2 class="card-title">联系我</h2>
        <p class="para muted">
          站点暂未开放评论与留言。如需交流，可以通过邮件联系。
        </p>
        <p class="para">
          <span class="contact-key">邮箱</span>
          <span class="contact-val">{{ contactEmail }}</span>
        </p>
      </el-card>
    </main>

    <footer class="site-footer">© {{ year }} 个人知识博客</footer>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

// 站点信息写死在前端：首版不做站点设置后台（已确认决策）
const frontendStack = ['Vue 3', 'JavaScript', 'Vite', 'Element Plus', 'Pinia', 'Vue Router', 'Axios']
const backendStack = ['Spring Boot 3.4', 'Java 21', 'MyBatis-Plus', 'Spring Security', 'JWT', 'MySQL 8']
const deployStack = ['Nginx', 'Docker Compose', 'Linux']

// 换成你自己的联系邮箱即可
const contactEmail = 'your-email@example.com'

const year = new Date().getFullYear()

function goHome() {
  router.push('/')
}
function goArticles() {
  router.push('/')
}
function goAdmin() {
  router.push('/admin')
}
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
.card {
  margin-bottom: 14px;
  border: none;
}
.card-title {
  margin: 0 0 12px;
  font-size: 18px;
  color: #303133;
}
h1.card-title {
  font-size: 22px;
}
.para {
  margin: 0 0 10px;
  color: #606266;
  font-size: 14px;
  line-height: 1.8;
}
.para.muted {
  color: #909399;
}
.stack-group {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}
.stack-label {
  flex: 0 0 48px;
  color: #909399;
  font-size: 13px;
  line-height: 24px;
}
.stack-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.contact-key {
  display: inline-block;
  width: 48px;
  color: #909399;
  font-size: 13px;
}
.contact-val {
  color: #303133;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 13px;
}
.site-footer {
  text-align: center;
  color: #909399;
  font-size: 12px;
  padding: 20px;
}
</style>
