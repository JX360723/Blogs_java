<template>
  <div class="login-page">
    <div class="login-card">
      <h2 class="login-title">博客管理后台</h2>
      <p class="login-sub">MVP 演示：账号 admin / admin123（mock）</p>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="onSubmit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            show-password
            :prefix-icon="Lock"
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <el-button type="primary" class="login-btn" :loading="loading" @click="onSubmit">
          登录
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: 'admin', password: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function onSubmit() {
  await formRef.value.validate()
  loading.value = true
  try {
    await authStore.login(form.username, form.password)
    ElMessage.success('登录成功')
    const redirect = route.query.redirect || '/admin/articles'
    router.replace(redirect)
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #eef3fb 0%, #f7f9fc 100%);
}
.login-card {
  width: 380px;
  background: #fff;
  border-radius: 10px;
  padding: 36px 32px;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.08);
}
.login-title {
  margin: 0 0 6px;
  text-align: center;
  color: #303133;
}
.login-sub {
  text-align: center;
  color: #909399;
  font-size: 12px;
  margin: 0 0 24px;
}
.login-btn {
  width: 100%;
}
</style>
