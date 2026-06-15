<template>
  <el-container class="admin-layout" :class="{ 'is-sidebar-collapsed': sidebarCollapsed }">
    <el-aside class="admin-layout-aside">
      <div class="admin-layout-brand">
        <div class="admin-layout-brand-mark">A</div>
        <div class="admin-layout-brand-copy">
          <strong>WHX Admin</strong>
          <span>运营管理后台</span>
        </div>
      </div>

      <nav class="admin-layout-nav">
        <el-tooltip
          v-for="item in menus"
          :key="item.path"
          :content="item.label"
          :disabled="!sidebarCollapsed"
          placement="right"
        >
          <button
            class="admin-layout-nav-item"
            :class="{ active: route.path === item.path }"
            :aria-label="item.label"
            type="button"
            @click="router.push(item.path)"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span class="admin-layout-nav-label">{{ item.label }}</span>
          </button>
        </el-tooltip>
      </nav>
    </el-aside>

    <el-container class="admin-layout-content">
      <el-header class="admin-layout-header">
        <div class="admin-layout-header-left">
          <div class="admin-layout-page-head">
            <div class="admin-layout-title">{{ currentPage.title }}</div>
            <el-breadcrumb class="admin-layout-breadcrumb" separator="/">
              <el-breadcrumb-item>WHX Admin</el-breadcrumb-item>
              <el-breadcrumb-item>{{ currentPage.title }}</el-breadcrumb-item>
            </el-breadcrumb>
          </div>
          <el-tag size="small" type="success">RBAC 权限控制</el-tag>
          <el-tag size="small" type="info">{{ currentPage.permission }}</el-tag>
        </div>

        <div class="admin-layout-header-right">
          <el-tooltip :content="sidebarCollapsed ? '展开侧栏' : '收起侧栏'" placement="bottom">
            <button class="admin-layout-icon-button" type="button" @click="toggleSidebar">
              <el-icon><component :is="sidebarCollapsed ? Expand : Fold" /></el-icon>
            </button>
          </el-tooltip>
          <el-dropdown trigger="click">
            <div class="admin-layout-user">
              <el-avatar :size="36">{{ userInitial }}</el-avatar>
              <div class="admin-layout-user-meta">
                <div class="admin-layout-user-name">{{ authStore.profile?.nickname || authStore.profile?.username || '管理员' }}</div>
                <div class="admin-layout-user-role">{{ roleLabel }}</div>
              </div>
              <el-icon class="admin-layout-user-arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>管理端账号</el-dropdown-item>
                <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="admin-layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ArrowDown, DataAnalysis, Expand, Fold, Key, Memo, Notebook, Setting, User } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const SIDEBAR_KEY = 'whx-admin-sidebar-collapsed'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const sidebarCollapsed = ref(false)

const menus = [
  { path: '/', label: '运营总览', icon: DataAnalysis },
  { path: '/users', label: '用户管理', icon: User },
  { path: '/roles', label: '角色权限', icon: Key },
  { path: '/dicts', label: '字典配置', icon: Setting },
  { path: '/notices', label: '公告管理', icon: Notebook },
  { path: '/logs', label: '操作日志', icon: Memo }
]

const pageMetaMap: Record<string, { title: string; permission: string }> = {
  '/': { title: '运营总览', permission: 'dashboard' },
  '/users': { title: '用户管理', permission: 'admin:user:list' },
  '/roles': { title: '角色权限', permission: 'admin:role:list' },
  '/dicts': { title: '字典配置', permission: 'admin:dict:list' },
  '/notices': { title: '公告管理', permission: 'admin:notice:list' },
  '/logs': { title: '操作日志', permission: 'admin:log:list' }
}

const currentPage = computed(() => pageMetaMap[route.path] || pageMetaMap['/'])
const userInitial = computed(() => (authStore.profile?.nickname || authStore.profile?.username || 'A').slice(0, 1))
const roleLabel = computed(() => authStore.profile?.roles?.join(' / ') || 'ROLE_ADMIN')

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

async function logout() {
  await authStore.logout()
  router.push('/login')
}

watch(sidebarCollapsed, (value) => {
  localStorage.setItem(SIDEBAR_KEY, value ? '1' : '0')
})

onMounted(async () => {
  sidebarCollapsed.value = localStorage.getItem(SIDEBAR_KEY) === '1'
  if (!authStore.profile && authStore.isAuthenticated) {
    await authStore.loadProfile()
  }
})
</script>
