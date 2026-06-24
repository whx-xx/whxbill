import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  { path: '/', redirect: () => (localStorage.getItem('whx-user-token') ? '/bills' : '/login') },
  { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { public: true, title: '登录' } },
  { path: '/403', component: () => import('@/views/exception/ExceptionView.vue'), props: { code: '403' }, meta: { public: true, title: '无权限访问' } },
  { path: '/404', component: () => import('@/views/exception/ExceptionView.vue'), props: { code: '404' }, meta: { public: true, title: '页面不存在' } },
  { path: '/500', component: () => import('@/views/exception/ExceptionView.vue'), props: { code: '500' }, meta: { public: true, title: '服务异常' } },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    children: [
      { path: 'profile', name: 'profile', component: () => import('@/views/ProfileView.vue'), meta: { title: '个人资料' } },
      { path: 'books', name: 'books', component: () => import('@/views/BooksView.vue'), meta: { title: '账本管理' } },
      { path: 'bills', name: 'bills', component: () => import('@/views/BillsView.vue'), meta: { title: '账单管理' } },
      { path: 'bill-import', name: 'bill-import', component: () => import('@/views/BillImportView.vue'), meta: { title: 'Excel导入' } },
      { path: 'categories', name: 'categories', component: () => import('@/views/CategoriesView.vue'), meta: { title: '分类管理' } },
      { path: 'accounts', name: 'accounts', component: () => import('@/views/AccountsView.vue'), meta: { title: '账户管理' } },
      { path: 'budgets', name: 'budgets', component: () => import('@/views/BudgetsView.vue'), meta: { title: '预算管理' } },
      { path: 'statistics', name: 'statistics', component: () => import('@/views/StatisticsView.vue'), meta: { title: '统计分析' } },
      { path: 'calendar', name: 'calendar', component: () => import('@/views/CalendarView.vue'), meta: { title: '日历视图' } },
      { path: 'messages', name: 'messages', component: () => import('@/views/MessagesView.vue'), meta: { title: '消息中心' } },
      { path: 'ocr', name: 'ocr', component: () => import('@/views/OcrView.vue'), meta: { title: 'OCR管理' } },
      { path: 'notices', redirect: { path: '/messages', query: { type: 'notice' } } },
      { path: 'notes', name: 'notes', component: () => import('@/views/NotesView.vue'), meta: { title: '我的笔记' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/404', meta: { public: true } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  // 公共页面直接放行，比如登录页、错误页。
  if (to.meta.public) {
    return true
  }
  // 没有登录态时，统一跳到登录页，并保留原始跳转地址。
  if (!authStore.isAuthenticated) {
    return {
      path: '/login',
      query: to.fullPath === '/' ? undefined : { redirect: to.fullPath }
    }
  }
  try {
    // 登录态存在时，先确保用户资料已加载，这样菜单和权限判断才完整。
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
  return true
})

export default router
