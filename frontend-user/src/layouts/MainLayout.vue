<template>
  <el-container
    class="main-layout"
    :class="{ 'is-sidebar-collapsed': layoutStore.sidebarCollapsed }"
    :style="layoutVars"
  >
    <el-aside class="main-layout-aside" :width="sidebarWidthPx" :style="sidebarStyle">
      <div class="main-layout-brand">
        <div class="main-layout-brand-mark">W</div>
        <div class="main-layout-brand-copy">
          <strong>WHX Bill</strong>
          <span>智能记账后台</span>
        </div>
      </div>

      <nav class="main-layout-nav">
        <el-tooltip
          v-for="item in menus"
          :key="item.path"
          :content="item.label"
          :disabled="!layoutStore.sidebarCollapsed"
          placement="right"
        >
          <button
            class="main-layout-nav-item"
            :class="{ active: route.path === item.path }"
            type="button"
            @click="router.push(item.path)"
          >
            <el-badge
              v-if="item.path === '/bill-import' && importWorkspaceRowCount > 0"
              :value="importWorkspaceRowCount"
              :offset="[3, 3]"
            >
              <el-icon><component :is="item.icon" /></el-icon>
            </el-badge>
            <el-icon v-else><component :is="item.icon" /></el-icon>
            <span class="main-layout-nav-label">{{ item.label }}</span>
          </button>
        </el-tooltip>
      </nav>
    </el-aside>

    <el-container class="main-layout-content">
      <el-header class="main-layout-header">
        <div class="main-layout-header-left">
          <div class="main-layout-page-head">
            <div class="main-layout-title">{{ currentTitle }}</div>
            <el-breadcrumb class="main-layout-breadcrumb" separator="/">
              <el-breadcrumb-item>WHX Bill</el-breadcrumb-item>
              <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
            </el-breadcrumb>
          </div>
          <el-select
            v-model="selectedBookId"
            placeholder="选择账本"
            class="main-layout-book-select"
            @change="handleBookChange"
          >
            <el-option
              v-for="item in books"
              :key="item.id"
              :label="item.bookName"
              :value="item.id"
            />
          </el-select>
        </div>

        <div class="main-layout-header-right">
          <el-tooltip :content="layoutStore.sidebarCollapsed ? '展开侧栏' : '收起侧栏'" placement="bottom">
            <button class="main-layout-icon-button" type="button" @click="layoutStore.toggleSidebar()">
              <el-icon><component :is="layoutStore.sidebarCollapsed ? Expand : Fold" /></el-icon>
            </button>
          </el-tooltip>
          <el-tooltip content="消息中心" placement="bottom">
            <button class="main-layout-icon-button" type="button" @click="router.push('/messages')">
              <el-badge
                v-if="notificationStore.unreadCount > 0"
                :value="notificationStore.unreadCount"
                :offset="[0, 4]"
              >
                <el-icon><Bell /></el-icon>
              </el-badge>
              <el-icon v-else><Bell /></el-icon>
            </button>
          </el-tooltip>
          <el-dropdown trigger="click">
            <div class="main-layout-user">
              <el-avatar :size="36" :src="avatarUrl || undefined">
                {{ userInitial }}
              </el-avatar>
              <div class="main-layout-user-meta">
                <div class="main-layout-user-name">{{ authStore.profile?.nickname || '记账用户' }}</div>
                <div class="main-layout-user-role">{{ roleLabel }}</div>
              </div>
              <el-icon class="main-layout-user-arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/profile')">个人资料</el-dropdown-item>
                <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  ArrowDown,
  Bell,
  Calendar,
  DataAnalysis,
  DocumentAdd,
  Expand,
  Files,
  Fold,
  Folder,
  Money,
  Opportunity,
  Notebook,
  Search,
  Tickets
} from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useLayoutStore } from '@/stores/layout'
import { useNotificationStore } from '@/stores/notification'
import { useBookContext } from '@/composables/useBookContext'
import { getImportWorkspaceRowCount, IMPORT_WORKSPACE_CHANGED_EVENT } from '@/utils/importWorkspace'
import { buildProxyFileUrl } from '@/utils/fileUrl'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const layoutStore = useLayoutStore()
const notificationStore = useNotificationStore()
const { books, currentBookId, loadBooks, setCurrentBookId } = useBookContext()
const selectedBookId = ref<number>()
const importWorkspaceRowCount = ref(0)

const baseMenus = [
  { path: '/books', label: '账本管理', icon: Files },
  { path: '/bills', label: '账单管理', icon: Tickets },
  { path: '/budgets', label: '预算管理', icon: Opportunity },
  { path: '/statistics', label: '统计分析', icon: DataAnalysis },
  { path: '/calendar', label: '日历视图', icon: Calendar },
  { path: '/categories', label: '分类管理', icon: Folder },
  { path: '/accounts', label: '账户管理', icon: Money },
  { path: '/ocr', label: 'OCR管理', icon: Search },
  { path: '/notes', label: '笔记公告', icon: Notebook }
]

const menus = computed(() => {
  const items = [...baseMenus]
  if (importWorkspaceRowCount.value > 0) {
    items.splice(2, 0, { path: '/bill-import', label: 'Excel导入', icon: DocumentAdd })
  }
  return items
})

const sidebarWidth = computed(() => (layoutStore.sidebarCollapsed ? 72 : 216))
const sidebarWidthPx = computed(() => `${sidebarWidth.value}px`)
const layoutVars = computed(() => ({
  '--sidebar-width': sidebarWidthPx.value
}))
const sidebarStyle = computed(() => ({
  width: sidebarWidthPx.value,
  minWidth: sidebarWidthPx.value,
  maxWidth: sidebarWidthPx.value,
  flexBasis: sidebarWidthPx.value,
  '--el-aside-width': sidebarWidthPx.value
}))
const currentTitle = computed(() => String(route.meta.title || menus.value.find((item) => item.path === route.path)?.label || '工作台'))
const userInitial = computed(() => (authStore.profile?.nickname || authStore.profile?.username || 'U').slice(0, 1))
const roleLabel = computed(() => authStore.profile?.roles?.join(' / ') || '普通成员')
const avatarUrl = computed(() => buildProxyFileUrl(authStore.profile?.avatarUrl))

async function logout() {
  notificationStore.disconnect()
  await authStore.logout()
  router.push('/login')
}

function handleBookChange(bookId: number) {
  setCurrentBookId(bookId)
}

function refreshImportWorkspaceRowCount() {
  importWorkspaceRowCount.value = getImportWorkspaceRowCount()
}

watch(currentBookId, (value) => {
  selectedBookId.value = value
}, { immediate: true })

onMounted(async () => {
  layoutStore.initialize()
  refreshImportWorkspaceRowCount()
  window.addEventListener(IMPORT_WORKSPACE_CHANGED_EVENT, refreshImportWorkspaceRowCount)
  window.addEventListener('storage', refreshImportWorkspaceRowCount)
  await authStore.loadProfile()
  await loadBooks()
  notificationStore.connect()
})

onUnmounted(() => {
  window.removeEventListener(IMPORT_WORKSPACE_CHANGED_EVENT, refreshImportWorkspaceRowCount)
  window.removeEventListener('storage', refreshImportWorkspaceRowCount)
  notificationStore.disconnect()
})
</script>

<style scoped>
.main-layout {
  min-height: 100vh;
  background: var(--layout-bg, #f1f3f5);
  --sidebar-width: 216px;
}

.main-layout-aside {
  position: fixed;
  width: 216px !important;
  min-width: 216px !important;
  max-width: 216px !important;
  flex-basis: 216px !important;
  --el-aside-width: 216px !important;
  top: 0;
  left: 0;
  bottom: 0;
  overflow-y: auto;
  z-index: 10;
  background: var(--layout-sidebar-bg, #ffffff);
  color: var(--layout-sidebar-muted, #8b9aaa);
  padding: 12px 10px;
  border-right: 1px solid var(--layout-border, #edf0f2);
  display: flex;
  flex-direction: column;
  transition: padding 0.16s ease;
}

.main-layout-brand {
  height: 48px;
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  align-items: center;
  column-gap: 10px;
  padding: 0;
  overflow: hidden;
}

.main-layout-brand-mark {
  width: 40px;
  height: 40px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  justify-self: center;
  flex-shrink: 0;
  background: linear-gradient(135deg, #23b39d, #9ee8da);
  color: #ffffff;
  font-size: 18px;
  font-weight: 900;
  box-shadow: 0 10px 24px rgba(35, 179, 157, 0.2);
}

.main-layout-brand :deep(.el-avatar) {
  justify-self: center;
  flex-shrink: 0;
}

.main-layout-brand-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  transition: opacity 0.16s ease;
}

.main-layout-brand-copy strong {
  color: var(--layout-sidebar-text, #18262e);
  font-size: 16px;
  line-height: 1.2;
}

.main-layout-brand-copy span {
  margin-top: 3px;
  color: var(--layout-sidebar-muted, #8b9aaa);
  font-size: 12px;
  white-space: nowrap;
}

.main-layout-nav {
  margin-top: 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.main-layout-nav-item {
  width: 100%;
  height: 44px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: var(--layout-sidebar-muted, #8d9aad);
  position: relative;
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  align-items: center;
  column-gap: 2px;
  padding: 0;
  cursor: pointer;
  font-size: 19px;
  text-align: left;
  transition: background-color 0.16s ease, color 0.16s ease;
}

.main-layout-nav-item:hover {
  background: var(--layout-sidebar-hover, #eff8f6);
  color: var(--brand);
}

.main-layout-nav-item.active {
  background: var(--layout-sidebar-active, #dff5ef);
  color: var(--brand);
}

.main-layout-nav-item > .el-icon,
.main-layout-nav-item :deep(.el-badge) {
  justify-self: center;
  line-height: 1;
}

.main-layout-nav-item :deep(.el-badge) {
  display: inline-grid;
  place-items: center;
}

.main-layout-nav-item :deep(.el-badge__content) {
  font-size: 10px;
}

.main-layout-nav-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: inherit;
  font-size: 14px;
  font-weight: 700;
  transition: opacity 0.14s ease;
}

.main-layout-content {
  min-height: 100vh;
  margin-left: 216px !important;
  transition: none;
}

.main-layout-header {
  position: fixed;
  top: 0;
  left: 216px !important;
  right: 0;
  z-index: 9;
  height: 68px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--layout-header-bg, rgba(241, 243, 245, 0.88));
  backdrop-filter: blur(18px);
  border-bottom: 1px solid var(--layout-border, rgba(210, 218, 224, 0.72));
  padding: 0 16px;
  transition: none;
}

.main-layout-header-left,
.main-layout-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.main-layout-header-left {
  min-width: 0;
}

.main-layout-page-head {
  min-width: 150px;
}

.main-layout-title {
  font-size: 18px;
  font-weight: 800;
  color: var(--text);
  line-height: 1.15;
}

.main-layout-breadcrumb {
  margin-top: 5px;
  font-size: 12px;
}

.main-layout-breadcrumb :deep(.el-breadcrumb__inner),
.main-layout-breadcrumb :deep(.el-breadcrumb__separator) {
  color: var(--muted);
}

.main-layout-book-select {
  width: 220px;
}

.main-layout-icon-button {
  width: 40px;
  height: 40px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  color: var(--layout-action-color, #6d7a89);
  display: grid;
  place-items: center;
  cursor: pointer;
  font-size: 19px;
  transition: all 0.16s ease;
}

.main-layout-icon-button:hover {
  background: var(--layout-action-hover, rgba(38, 166, 154, 0.08));
  border-color: var(--layout-border, #ccece5);
  color: var(--brand);
}

.main-layout-user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 12px;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.main-layout-user:hover {
  background: var(--layout-action-hover, rgba(38, 166, 154, 0.08));
}

.main-layout-user-meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.main-layout-user-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}

.main-layout-user-role {
  font-size: 12px;
  color: var(--muted);
}

.main-layout-user-arrow {
  color: var(--muted);
}

.main-layout-main {
  margin-top: 68px;
  min-height: 100vh;
  padding: 12px 16px 16px;
  overflow-x: hidden;
}

.main-layout.is-sidebar-collapsed .main-layout-aside {
  width: 72px !important;
  min-width: 72px !important;
  max-width: 72px !important;
  flex-basis: 72px !important;
  --el-aside-width: 72px !important;
  padding: 12px 8px;
}

.main-layout.is-sidebar-collapsed .main-layout-content {
  margin-left: 72px !important;
}

.main-layout.is-sidebar-collapsed .main-layout-header {
  left: 72px !important;
}

.main-layout.is-sidebar-collapsed .main-layout-brand {
  grid-template-columns: 1fr;
}

.main-layout.is-sidebar-collapsed .main-layout-brand-mark {
  width: 44px;
  height: 44px;
  border-radius: 16px;
  font-size: 18px;
}

.main-layout.is-sidebar-collapsed .main-layout-brand-copy,
.main-layout.is-sidebar-collapsed .main-layout-nav-label {
  display: none;
  pointer-events: none;
}

.main-layout.is-sidebar-collapsed .main-layout-nav-item {
  width: 44px;
  min-width: 44px;
  max-width: 44px;
  height: 44px;
  min-height: 44px;
  grid-template-columns: 1fr;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-inline: auto;
  padding: 0;
  border-radius: 14px;
  text-align: center;
}

.main-layout.is-sidebar-collapsed .main-layout-nav-item > .el-icon,
.main-layout.is-sidebar-collapsed .main-layout-nav-item :deep(.el-badge) {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  justify-self: center;
  align-self: center;
  margin: 0;
  line-height: 1;
}

.main-layout.is-sidebar-collapsed .main-layout-nav-item :deep(.el-icon) {
  width: 19px;
  height: 19px;
  margin: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.main-layout.is-sidebar-collapsed .main-layout-nav-item :deep(.el-icon svg) {
  display: block;
}

@media (max-width: 760px) {
  .main-layout {
    --sidebar-width: 56px !important;
  }

  .main-layout-aside {
    width: 56px !important;
    min-width: 56px !important;
    max-width: 56px !important;
    flex-basis: 56px !important;
    --el-aside-width: 56px !important;
    padding: 8px 5px;
  }

  .main-layout-brand-copy,
  .main-layout-nav-label,
  .main-layout-user-meta,
  .main-layout-user-arrow {
    display: none;
  }

  .main-layout-nav {
    gap: 6px;
  }

  .main-layout-nav-item {
    width: 42px;
    height: 42px;
    grid-template-columns: 1fr;
    margin-inline: auto;
  }

  .main-layout-header {
    left: 56px !important;
    height: auto;
    min-height: 64px;
    padding: 8px 10px;
    align-items: flex-start;
    gap: 8px;
  }

  .main-layout-header-left {
    flex: 1;
    min-width: 0;
    flex-wrap: wrap;
    gap: 8px;
  }

  .main-layout-title {
    font-size: 16px;
  }

  .main-layout-book-select {
    width: min(190px, 100%);
  }

  .main-layout-main {
    margin-top: 84px;
    padding: 10px;
  }

  .main-layout-content {
    margin-left: 56px !important;
  }
}
</style>
