<template>
  <el-container
    class="main-layout"
    :class="{ 'is-sidebar-collapsed': layoutStore.sidebarCollapsed }"
    :style="layoutVars"
  >
    <el-aside class="main-layout-aside" :width="sidebarWidthRem" :style="sidebarStyle">
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
            @visible-change="handleBookSelectVisibleChange"
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

  <el-dialog
    v-model="noticeDialogVisible"
    class="notice-popup-dialog"
    width="min(42rem, calc(100vw - 2rem))"
    :show-close="false"
    append-to-body
  >
    <template #header>
      <div class="notice-popup-head">
        <div class="notice-popup-title">
          <span><el-icon><Bell /></el-icon></span>
          <strong>公告</strong>
        </div>
        <button type="button" class="notice-popup-close" @click="markActiveNoticeRead">
          <el-icon><Close /></el-icon>
        </button>
      </div>
    </template>
    <button
      v-if="activeNotice"
      type="button"
      class="notice-popup-row"
      @click="openNoticeCenter"
    >
      <span class="notice-popup-row-icon"><el-icon><Bell /></el-icon></span>
      <span>
        <strong>{{ activeNotice.title }}</strong>
        <small>{{ noticeExcerpt(activeNotice) }}</small>
      </span>
      <el-icon><ArrowRight /></el-icon>
    </button>
    <template #footer>
      <div class="notice-popup-footer">
        <span>{{ activeNotice ? formatNoticeTime(activeNotice.createdTime) : '' }}</span>
        <el-button type="primary" plain @click="markActiveNoticeRead">我知道了</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  ArrowDown,
  ArrowRight,
  Bell,
  Calendar,
  Close,
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
import request from '@/utils/request'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const layoutStore = useLayoutStore()
const notificationStore = useNotificationStore()
const { books, currentBookId, loadBooks, setCurrentBookId } = useBookContext()
const selectedBookId = ref<number>()
const importWorkspaceRowCount = ref(0)
const activeNotice = ref<any>()
const noticeDialogVisible = ref(false)

const baseMenus = [
  { path: '/books', label: '账本管理', icon: Files },
  { path: '/bills', label: '账单管理', icon: Tickets },
  { path: '/bill-import', label: 'Excel导入', icon: DocumentAdd },
  { path: '/budgets', label: '预算管理', icon: Opportunity },
  { path: '/statistics', label: '统计分析', icon: DataAnalysis },
  { path: '/calendar', label: '日历视图', icon: Calendar },
  { path: '/categories', label: '分类管理', icon: Folder },
  { path: '/accounts', label: '账户管理', icon: Money },
  { path: '/ocr', label: 'OCR管理', icon: Search },
  { path: '/notes', label: '我的笔记', icon: Notebook }
]

const menus = computed(() => baseMenus)

const sidebarWidth = computed(() => (layoutStore.sidebarCollapsed ? 72 : 216))
const sidebarWidthRem = computed(() => `${sidebarWidth.value / 16}rem`)
const layoutVars = computed(() => ({
  '--sidebar-width': sidebarWidthRem.value
}))
const sidebarStyle = computed(() => ({
  width: sidebarWidthRem.value,
  minWidth: sidebarWidthRem.value,
  maxWidth: sidebarWidthRem.value,
  flexBasis: sidebarWidthRem.value,
  '--el-aside-width': sidebarWidthRem.value
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

async function handleBookSelectVisibleChange(visible: boolean) {
  if (visible) {
    await loadBooks(true)
  }
}

function refreshImportWorkspaceRowCount() {
  importWorkspaceRowCount.value = getImportWorkspaceRowCount()
}

function isNoticeMessage(message: any) {
  return String(message?.messageType || '').toUpperCase() === 'NOTICE' || String(message?.title || '').includes('公告')
}

async function markActiveNoticeRead() {
  if (activeNotice.value?.id && activeNotice.value.readStatus === 0) {
    await request.post(`/api/user/messages/${activeNotice.value.id}/read`)
    await notificationStore.loadMessages()
  }
  noticeDialogVisible.value = false
}

async function loadUnreadNoticePopup() {
  if (!notificationStore.messages.length) {
    await notificationStore.loadMessages()
  }
  activeNotice.value = notificationStore.messages.find((message) => message.readStatus === 0 && isNoticeMessage(message))
  noticeDialogVisible.value = Boolean(activeNotice.value)
}

async function openNoticeCenter() {
  const messageId = activeNotice.value?.id
  await markActiveNoticeRead()
  router.push({
    path: '/messages',
    query: {
      type: 'notice',
      focus: String(messageId || '')
    }
  })
}

function noticeExcerpt(notice: any) {
  return String(notice?.content || '').slice(0, 48) || '点击查看公告详情'
}

function formatNoticeTime(value?: string) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16).replace(/-/g, '/')
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
  await loadBooks(true)
  await notificationStore.connect()
  await loadUnreadNoticePopup()
})

onUnmounted(() => {
  window.removeEventListener(IMPORT_WORKSPACE_CHANGED_EVENT, refreshImportWorkspaceRowCount)
  window.removeEventListener('storage', refreshImportWorkspaceRowCount)
  notificationStore.disconnect()
})
</script>

<style scoped lang="stylus">
.main-layout {
  min-height: 100vh;
  background: var(--layout-bg, #f1f3f5);
  --sidebar-width: 13.5rem;
}

.main-layout-aside {
  position: fixed;
  width: 13.5rem !important;
  min-width: 13.5rem !important;
  max-width: 13.5rem !important;
  flex-basis: 13.5rem !important;
  --el-aside-width: 13.5rem !important;
  top: 0;
  left: 0;
  bottom: 0;
  overflow-y: auto;
  z-index: 10;
  background: var(--layout-sidebar-bg, #ffffff);
  color: var(--layout-sidebar-muted, #8b9aaa);
  padding: 0.75rem 0.625rem;
  border-right: 0.0625rem solid var(--layout-border, #edf0f2);
  display: flex;
  flex-direction: column;
  transition: padding 0.16s ease;
}

.main-layout-brand {
  height: 3rem;
  display: grid;
  grid-template-columns: 2.75rem minmax(0, 1fr);
  align-items: center;
  column-gap: 0.625rem;
  padding: 0;
  overflow: hidden;
}

.main-layout-brand-mark {
  width: 2.5rem;
  height: 2.5rem;
  border-radius: 0.875rem;
  display: grid;
  place-items: center;
  justify-self: center;
  flex-shrink: 0;
  background: linear-gradient(135deg, #23b39d, #9ee8da);
  color: #ffffff;
  font-size: 1.125rem;
  font-weight: 900;
  box-shadow: 0 0.625rem 1.5rem rgba(35, 179, 157, 0.2);
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
  font-size: 1rem;
  line-height: 1.2;
}

.main-layout-brand-copy span {
  margin-top: 0.1875rem;
  color: var(--layout-sidebar-muted, #8b9aaa);
  font-size: 0.75rem;
  white-space: nowrap;
}

.main-layout-nav {
  margin-top: 1.125rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.main-layout-nav-item {
  width: 100%;
  height: 2.75rem;
  border: 0;
  border-radius: 0.75rem;
  background: transparent;
  color: var(--layout-sidebar-muted, #8d9aad);
  position: relative;
  display: grid;
  grid-template-columns: 2.75rem minmax(0, 1fr);
  align-items: center;
  column-gap: 0.125rem;
  padding: 0;
  cursor: pointer;
  font-size: 1.1875rem;
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
  font-size: 0.625rem;
}

.main-layout-nav-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: inherit;
  font-size: 0.875rem;
  font-weight: 700;
  transition: opacity 0.14s ease;
}

.main-layout-content {
  min-height: 100vh;
  margin-left: 13.5rem !important;
  transition: none;
}

.main-layout-header {
  position: fixed;
  top: 0;
  left: 13.5rem !important;
  right: 0;
  z-index: 9;
  height: 4.25rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--layout-header-bg, rgba(241, 243, 245, 0.88));
  backdrop-filter: blur(1.125rem);
  border-bottom: 0.0625rem solid var(--layout-border, rgba(210, 218, 224, 0.72));
  padding: 0 1rem;
  transition: none;
}

.main-layout-header-left,
.main-layout-header-right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.main-layout-header-left {
  min-width: 0;
}

.main-layout-page-head {
  min-width: 9.375rem;
}

.main-layout-title {
  font-size: 1.125rem;
  font-weight: 800;
  color: var(--text);
  line-height: 1.15;
}

.main-layout-breadcrumb {
  margin-top: 0.3125rem;
  font-size: 0.75rem;
}

.main-layout-breadcrumb :deep(.el-breadcrumb__inner),
.main-layout-breadcrumb :deep(.el-breadcrumb__separator) {
  color: var(--muted);
}

.main-layout-book-select {
  width: 13.75rem;
}

.main-layout-icon-button {
  width: 2.5rem;
  height: 2.5rem;
  border: 0.0625rem solid transparent;
  border-radius: 0.75rem;
  background: transparent;
  color: var(--layout-action-color, #6d7a89);
  display: grid;
  place-items: center;
  cursor: pointer;
  font-size: 1.1875rem;
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
  gap: 0.625rem;
  padding: 0.5rem 0.625rem;
  border-radius: 0.75rem;
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
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--text);
}

.main-layout-user-role {
  font-size: 0.75rem;
  color: var(--muted);
}

.main-layout-user-arrow {
  color: var(--muted);
}

.main-layout-main {
  margin-top: 4.25rem;
  min-height: 100vh;
  padding: 0.75rem 1rem 1rem;
  overflow-x: hidden;
}

.main-layout.is-sidebar-collapsed .main-layout-aside {
  width: 4.5rem !important;
  min-width: 4.5rem !important;
  max-width: 4.5rem !important;
  flex-basis: 4.5rem !important;
  --el-aside-width: 4.5rem !important;
  padding: 0.75rem 0.5rem;
}

.main-layout.is-sidebar-collapsed .main-layout-content {
  margin-left: 4.5rem !important;
}

.main-layout.is-sidebar-collapsed .main-layout-header {
  left: 4.5rem !important;
}

.main-layout.is-sidebar-collapsed .main-layout-brand {
  grid-template-columns: 1fr;
}

.main-layout.is-sidebar-collapsed .main-layout-brand-mark {
  width: 2.75rem;
  height: 2.75rem;
  border-radius: 1rem;
  font-size: 1.125rem;
}

.main-layout.is-sidebar-collapsed .main-layout-brand-copy,
.main-layout.is-sidebar-collapsed .main-layout-nav-label {
  display: none;
  pointer-events: none;
}

.main-layout.is-sidebar-collapsed .main-layout-nav-item {
  width: 2.75rem;
  min-width: 2.75rem;
  max-width: 2.75rem;
  height: 2.75rem;
  min-height: 2.75rem;
  grid-template-columns: 1fr;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-inline: auto;
  padding: 0;
  border-radius: 0.875rem;
  text-align: center;
}

.main-layout.is-sidebar-collapsed .main-layout-nav-item > .el-icon,
.main-layout.is-sidebar-collapsed .main-layout-nav-item :deep(.el-badge) {
  width: 2.75rem;
  height: 2.75rem;
  display: grid;
  place-items: center;
  justify-self: center;
  align-self: center;
  margin: 0;
  line-height: 1;
}

.main-layout.is-sidebar-collapsed .main-layout-nav-item :deep(.el-icon) {
  width: 1.1875rem;
  height: 1.1875rem;
  margin: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.main-layout.is-sidebar-collapsed .main-layout-nav-item :deep(.el-icon svg) {
  display: block;
}

:global(.notice-popup-dialog) {
  border-radius: 1rem;
  overflow: hidden;
}

:global(.notice-popup-dialog .el-dialog__header) {
  padding: 0;
  margin: 0;
}

:global(.notice-popup-dialog .el-dialog__body) {
  padding: 0;
}

:global(.notice-popup-dialog .el-dialog__footer) {
  padding: 0;
}

.notice-popup-head,
.notice-popup-footer,
.notice-popup-row {
  display: flex;
  align-items: center;
}

.notice-popup-head {
  justify-content: space-between;
  gap: 0.75rem;
  padding: 1rem 1.25rem;
  background: linear-gradient(135deg, #f7fbff 0%, #ffffff 100%);
  border-bottom: 0.0625rem solid #eef2f5;
}

.notice-popup-title {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  color: #132933;
  font-size: 1.125rem;
  font-weight: 900;
}

.notice-popup-title span {
  width: 2.25rem;
  height: 2.25rem;
  display: grid;
  place-items: center;
  border-radius: 0.625rem;
  color: #fff;
  background: linear-gradient(135deg, #26a69a, #43c6b6);
  box-shadow: 0 0.5rem 1rem rgba(38, 166, 154, 0.22);
}

.notice-popup-close {
  width: 2.25rem;
  height: 2.25rem;
  border: 0;
  border-radius: 0.625rem;
  display: grid;
  place-items: center;
  color: #6d7a89;
  background: #f5f8fa;
  cursor: pointer;
}

.notice-popup-row {
  width: 100%;
  min-height: 4.5rem;
  gap: 0.75rem;
  padding: 1rem 1.25rem;
  border: 0;
  color: inherit;
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.notice-popup-row:hover {
  background: #f8fcfb;
}

.notice-popup-row-icon {
  width: 2.5rem;
  height: 2.5rem;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 0.75rem;
  color: #168f82;
  background: #e8f7f4;
}

.notice-popup-row span:nth-child(2) {
  flex: 1;
  min-width: 0;
}

.notice-popup-row strong,
.notice-popup-row small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-popup-row strong {
  color: #132933;
  font-size: 0.9375rem;
}

.notice-popup-row small {
  margin-top: 0.25rem;
  color: #71808d;
  font-size: 0.8125rem;
}

.notice-popup-footer {
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.875rem 1.25rem;
  border-top: 0.0625rem solid #eef2f5;
  background: #fbfcfd;
}

.notice-popup-footer span {
  color: #71808d;
  font-size: 0.8125rem;
}

@media (max-width: 47.5rem) {
  .main-layout {
    --sidebar-width: 3.5rem !important;
  }

  .main-layout-aside {
    width: 3.5rem !important;
    min-width: 3.5rem !important;
    max-width: 3.5rem !important;
    flex-basis: 3.5rem !important;
    --el-aside-width: 3.5rem !important;
    padding: 0.5rem 0.3125rem;
  }

  .main-layout-brand-copy,
  .main-layout-nav-label,
  .main-layout-user-meta,
  .main-layout-user-arrow {
    display: none;
  }

  .main-layout-nav {
    gap: 0.375rem;
  }

  .main-layout-nav-item {
    width: 2.625rem;
    height: 2.625rem;
    grid-template-columns: 1fr;
    margin-inline: auto;
  }

  .main-layout-header {
    left: 3.5rem !important;
    height: auto;
    min-height: 4rem;
    padding: 0.5rem 0.625rem;
    align-items: flex-start;
    gap: 0.5rem;
  }

  .main-layout-header-left {
    flex: 1;
    min-width: 0;
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .main-layout-title {
    font-size: 1rem;
  }

  .main-layout-book-select {
    width: min(11.875rem, 100%);
  }

  .main-layout-main {
    margin-top: 5.25rem;
    padding: 0.625rem;
  }

  .main-layout-content {
    margin-left: 3.5rem !important;
  }
}
</style>
