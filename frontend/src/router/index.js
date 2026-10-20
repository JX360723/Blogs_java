import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  // ---- 访客端 ----
  {
    path: '/',
    name: 'ArticleList',
    component: () => import('../views/public/ArticleList.vue')
  },
  {
    path: '/article/:id',
    name: 'ArticleDetail',
    component: () => import('../views/public/ArticleDetail.vue'),
    props: true
  },
  // ---- 登录 ----
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/auth/Login.vue')
  },
  // ---- 管理端 ----
  {
    path: '/admin',
    component: () => import('../layouts/AdminLayout.vue'),
    redirect: '/admin/articles',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'articles',
        name: 'AdminArticleList',
        component: () => import('../views/admin/ArticleManage.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: 'categories',
        name: 'AdminCategoryList',
        component: () => import('../views/admin/CategoryManage.vue'),
        meta: { requiresAuth: true }
      }
    ]
  },
  // ---- 404 ----
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('../views/public/NotFound.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：管理端需登录；标签/站点设置等阶段外入口不注册
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('blog_token')
  if (to.meta.requiresAuth && !token) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  if (to.name === 'Login' && token) {
    next({ path: '/admin/articles' })
    return
  }
  next()
})

export default router
