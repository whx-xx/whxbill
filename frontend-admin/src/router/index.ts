import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
  { path: '/403', component: () => import('@/views/exception/ExceptionView.vue'), props: { code: '403' }, meta: { public: true, title: '无权限访问' } },
  { path: '/404', component: () => import('@/views/exception/ExceptionView.vue'), props: { code: '404' }, meta: { public: true, title: '页面不存在' } },
  { path: '/500', component: () => import('@/views/exception/ExceptionView.vue'), props: { code: '500' }, meta: { public: true, title: '服务异常' } },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    children: [
      { path: '', component: () => import('@/views/DashboardView.vue'), meta: { title: '运营总览' } },
      { path: 'users', component: () => import('@/views/UsersView.vue'), meta: { title: '用户管理', permission: 'admin:user:list' } },
      { path: 'roles', component: () => import('@/views/RolesView.vue'), meta: { title: '角色权限', permission: 'admin:role:list' } },
      { path: 'dicts', component: () => import('@/views/DictsView.vue'), meta: { title: '字典配置', permission: 'admin:dict:list' } },
      { path: 'notices', component: () => import('@/views/NoticesView.vue'), meta: { title: '公告管理', permission: 'admin:notice:list' } },
      { path: 'logs', component: () => import('@/views/LogsView.vue'), meta: { title: '操作日志', permission: 'admin:log:list' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/404', meta: { public: true } }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.VITE_APP_BASE || '/'),
  routes
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  if (to.meta.public) {
    return true
  }
  if (!authStore.isAuthenticated) {
    return {
      path: '/login',
      query: to.fullPath === '/' ? undefined : { redirect: to.fullPath }
    }
  }
  try {
    if (!authStore.profile || !authStore.profile.permissions || !authStore.profile.permissions.length) {
      await authStore.loadProfile()
    }
  } catch (error) {
    authStore.clearSession()
    return {
      path: '/login',
      query: to.fullPath === '/' ? undefined : { redirect: to.fullPath }
    }
  }
  if (to.meta.permission && !authStore.permissions.includes(to.meta.permission)) {
    return '/403'
  }
  return true
})

export default router
