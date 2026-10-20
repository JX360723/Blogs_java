import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: true,
    proxy: {
      // 统一代理 /api 到后端 Spring Boot（本地联调约定，见 docs/INTEGRATION_PLAN.md 5.2）
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
