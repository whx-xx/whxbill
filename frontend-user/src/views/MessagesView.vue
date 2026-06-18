<template>
  <div class="message-shell">
    <section class="message-head">
      <div>
        <div class="manage-section-eyebrow">Notifications</div>
        <h2>通知中心</h2>
        <p>查看预算提醒、公告推送和系统通知，未读消息会同步到右上角角标。</p>
      </div>
      <div class="message-head-actions">
        <el-tag :type="notificationStore.connected ? 'success' : 'info'" effect="plain">
          {{ notificationStore.connected ? '实时已连接' : '实时未连接' }}
        </el-tag>
        <el-button :icon="Refresh" :loading="loading" @click="loadMessages">刷新</el-button>
        <el-button type="primary" :disabled="!unreadMessages.length" :loading="markingAll" @click="markAllRead">全部已读</el-button>
      </div>
    </section>

    <section class="message-stat-grid">
      <div class="message-stat-card">
        <span>消息总数</span>
        <strong>{{ messages.length }}</strong>
        <small>全部通知记录</small>
      </div>
      <div class="message-stat-card unread">
        <span>未读消息</span>
        <strong>{{ unreadMessages.length }}</strong>
        <small>需要处理</small>
      </div>
      <div class="message-stat-card read">
        <span>已读消息</span>
        <strong>{{ readMessages.length }}</strong>
        <small>已确认</small>
      </div>
      <div class="message-stat-card notice">
        <span>公告通知</span>
        <strong>{{ noticeMessages.length }}</strong>
        <small>平台公告</small>
      </div>
    </section>

    <section class="message-control-card">
      <el-input v-model="keyword" clearable class="message-search" placeholder="搜索标题或内容" :prefix-icon="Search" />
      <el-segmented v-model="statusFilter" :options="statusOptions" />
    </section>

    <section class="message-list-card">
      <div class="message-list-head">
        <div>
          <h3>消息列表</h3>
          <span>当前显示 {{ filteredMessages.length }} 条</span>
        </div>
      </div>

      <el-empty v-if="!filteredMessages.length" description="暂无消息" />
      <div v-else class="message-list">
        <article
          v-for="message in filteredMessages"
          :key="message.id"
          class="message-item"
          :class="{ unread: message.readStatus === 0 }"
        >
          <span class="message-dot"></span>
          <div class="message-main">
            <div class="message-title-row">
              <h4>{{ message.title }}</h4>
              <span class="message-tags">
                <el-tag v-if="isNoticeMessage(message)" type="success" effect="plain">公告</el-tag>
                <el-tag v-else-if="isBudgetMessage(message)" type="warning" effect="plain">预算</el-tag>
                <el-tag :type="message.readStatus === 0 ? 'primary' : 'info'" effect="plain">
                  {{ message.readStatus === 0 ? '未读' : '已读' }}
                </el-tag>
              </span>
            </div>
            <p>{{ message.content }}</p>
            <small>{{ formatTime(message.createdTime) }}</small>
          </div>
          <div class="message-actions">
            <el-button
              v-if="isBudgetMessage(message)"
              size="small"
              type="warning"
              plain
              @click="openBudgetMessage(message)"
            >
              查看预算
            </el-button>
            <el-button
              v-if="isNoticeMessage(message)"
              size="small"
              type="success"
              plain
              @click="openNoticeMessage(message)"
            >
              查看公告
            </el-button>
            <el-button v-if="message.readStatus === 0" size="small" type="primary" plain @click="markRead(message.id)">标记已读</el-button>
          </div>
        </article>
      </div>
    </section>

    <el-dialog
      v-model="noticeDialogVisible"
      class="message-notice-dialog"
      width="min(46rem, calc(100vw - 2rem))"
      append-to-body
    >
      <template #header>
        <div class="message-notice-head">
          <span><el-icon><Bell /></el-icon></span>
          <div>
            <small>平台公告</small>
            <h3>{{ selectedNoticeTitle }}</h3>
          </div>
        </div>
      </template>
      <article class="message-notice-content">
        <div class="message-notice-meta">{{ formatTime(selectedNotice?.createdTime || selectedNoticeMessage?.createdTime) }}</div>
        <div v-if="selectedNoticeHtml" class="message-rich-text" v-html="selectedNoticeHtml"></div>
        <p v-else>{{ selectedNoticeMessage?.content || '暂无公告内容' }}</p>
      </article>
      <template #footer>
        <div class="message-notice-footer">
          <span>公告已合并到消息中心，读过后不会再次弹出。</span>
          <el-button type="primary" @click="noticeDialogVisible = false">我知道了</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Bell, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import { useNotificationStore } from '@/stores/notification'

const route = useRoute()
const router = useRouter()
const notificationStore = useNotificationStore()
const messages = computed(() => notificationStore.messages)
const loading = ref(false)
const markingAll = ref(false)
const keyword = ref('')
const statusFilter = ref<'ALL' | 'UNREAD' | 'READ' | 'NOTICE' | 'BUDGET'>('ALL')
const noticeDialogVisible = ref(false)
const notices = ref<any[]>([])
const selectedNoticeMessage = ref<any>()
const selectedNotice = ref<any>()

const statusOptions = [
  { label: '全部', value: 'ALL' },
  { label: '未读', value: 'UNREAD' },
  { label: '已读', value: 'READ' },
  { label: '公告', value: 'NOTICE' },
  { label: '预算', value: 'BUDGET' }
]

const unreadMessages = computed(() => messages.value.filter((item) => item.readStatus === 0))
const readMessages = computed(() => messages.value.filter((item) => item.readStatus !== 0))
const noticeMessages = computed(() => messages.value.filter(isNoticeMessage))
const selectedNoticeTitle = computed(() => selectedNotice.value?.title || normalizeNoticeTitle(selectedNoticeMessage.value?.title) || '公告详情')
const selectedNoticeHtml = computed(() => selectedNotice.value?.content ? sanitizeHtml(selectedNotice.value.content) : '')

const filteredMessages = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return messages.value.filter((item) => {
    const matchStatus =
      statusFilter.value === 'ALL'
      || (statusFilter.value === 'UNREAD' && item.readStatus === 0)
      || (statusFilter.value === 'READ' && item.readStatus !== 0)
      || (statusFilter.value === 'NOTICE' && isNoticeMessage(item))
      || (statusFilter.value === 'BUDGET' && isBudgetMessage(item))
    const matchKeyword = !q || `${item.title || ''} ${item.content || ''}`.toLowerCase().includes(q)
    return matchStatus && matchKeyword
  })
})

async function loadMessages() {
  if (loading.value) return
  loading.value = true
  try {
    await notificationStore.loadMessages()
    applyRouteFilter()
  } finally {
    loading.value = false
  }
}

async function loadNotices() {
  if (notices.value.length) return
  notices.value = await request.get('/api/user/notices')
}

async function markRead(messageId: number) {
  await request.post(`/api/user/messages/${messageId}/read`)
  await loadMessages()
  ElMessage.success('消息已标记为已读')
}

async function markAllRead() {
  if (!unreadMessages.value.length || markingAll.value) return
  markingAll.value = true
  try {
    await Promise.all(unreadMessages.value.map((message) => request.post(`/api/user/messages/${message.id}/read`)))
    await loadMessages()
    ElMessage.success('未读消息已全部标记')
  } finally {
    markingAll.value = false
  }
}

function isBudgetMessage(message: any) {
  const text = `${message?.title || ''} ${message?.content || ''}`
  return text.includes('预算')
}

function isNoticeMessage(message: any) {
  return String(message?.messageType || '').toUpperCase() === 'NOTICE' || String(message?.title || '').includes('公告')
}

function parseBudgetMonth(message: any) {
  const text = `${message?.title || ''} ${message?.content || ''}`
  return text.match(/\d{4}-\d{2}/)?.[0]
}

function openBudgetMessage(message: any) {
  const month = parseBudgetMonth(message)
  router.push({
    path: '/budgets',
    query: {
      month: month || '',
      focus: 'total',
      fromMessage: String(message.id || '')
    }
  })
}

async function openNoticeMessage(message: any) {
  selectedNoticeMessage.value = message
  selectedNotice.value = undefined
  await loadNotices()
  selectedNotice.value = findNoticeForMessage(message)
  if (message.readStatus === 0) {
    await request.post(`/api/user/messages/${message.id}/read`)
    await loadMessages()
  }
  noticeDialogVisible.value = true
}

function findNoticeForMessage(message: any) {
  const title = normalizeNoticeTitle(message?.title)
  return notices.value.find((notice) => normalizeNoticeTitle(notice?.title) === title)
}

function normalizeNoticeTitle(title?: string) {
  return String(title || '').replace(/^新公告[:：]\s*/, '').trim()
}

function sanitizeHtml(html: string) {
  const container = document.createElement('div')
  container.innerHTML = html || ''
  container.querySelectorAll('script, iframe, object, embed').forEach((node) => node.remove())
  return container.innerHTML
}

function applyRouteFilter() {
  if (route.query.type === 'notice') {
    statusFilter.value = 'NOTICE'
  }
}

async function applyRouteFocus() {
  applyRouteFilter()
  const focusId = Number(route.query.focus || route.query.fromMessage)
  if (!focusId) return
  const target = messages.value.find((message) => Number(message.id) === focusId)
  if (target && isNoticeMessage(target)) {
    await openNoticeMessage(target)
  }
}

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

watch(() => route.query, applyRouteFocus)

onMounted(async () => {
  await loadMessages()
  await applyRouteFocus()
})
</script>

<style scoped lang="stylus">
.message-shell {
  display: flex;
  flex-direction: column;
  gap: 0.875rem;
}

.message-head,
.message-control-card,
.message-list-card,
.message-stat-card {
  background: #fff;
  border: 0.0625rem solid #e5ebf0;
  border-radius: 0.5rem;
}

.message-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  padding: 1.25rem;
}

.message-head h2,
.message-list-head h3,
.message-item h4 {
  margin: 0;
  color: #10242f;
}

.message-head h2 {
  font-size: 1.5rem;
}

.message-head p,
.message-list-head span,
.message-stat-card span,
.message-stat-card small,
.message-item p,
.message-item small {
  color: #7d8d9a;
  font-size: 0.8125rem;
}

.message-head p {
  margin: 0.5rem 0 0;
}

.message-head-actions {
  display: flex;
  gap: 0.625rem;
}

.message-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.75rem;
}

.message-stat-card {
  min-height: 6rem;
  padding: 1rem;
  border-left: 0.25rem solid #d8e4ea;
  display: grid;
  align-content: center;
  gap: 0.375rem;
}

.message-stat-card strong {
  color: #10242f;
  font-size: 1.625rem;
  line-height: 1;
}

.message-stat-card.unread {
  border-left-color: #238be6;
}

.message-stat-card.read {
  border-left-color: #22a879;
}

.message-stat-card.notice {
  border-left-color: #26a69a;
}

.message-control-card {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.875rem 1rem;
  flex-wrap: wrap;
}

.message-search {
  width: min(23.75rem, 100%);
}

.message-list-card {
  padding: 1rem;
}

.message-list-head {
  margin-bottom: 0.875rem;
}

.message-list {
  display: grid;
  gap: 0.625rem;
}

.message-item {
  display: grid;
  grid-template-columns: 0.625rem minmax(0, 1fr) auto;
  gap: 0.75rem;
  align-items: start;
  padding: 0.875rem;
  border: 0.0625rem solid #edf1f4;
  border-radius: 0.5rem;
  background: #fbfcfd;
}

.message-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.message-item.unread {
  border-color: #cfe5ff;
  background: #f7fbff;
}

.message-dot {
  width: 0.5rem;
  height: 0.5rem;
  margin-top: 0.4375rem;
  border-radius: 50%;
  background: #b8c4cc;
}

.message-item.unread .message-dot {
  background: #238be6;
}

.message-main {
  min-width: 0;
}

.message-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.message-tags {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 0.375rem;
}

.message-title-row h4 {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-item p {
  margin: 0.5rem 0;
  line-height: 1.6;
}

:global(.message-notice-dialog) {
  border-radius: 1rem;
  overflow: hidden;
}

:global(.message-notice-dialog .el-dialog__header) {
  padding: 0;
  margin: 0;
}

:global(.message-notice-dialog .el-dialog__body) {
  padding: 0;
}

:global(.message-notice-dialog .el-dialog__footer) {
  padding: 0;
}

.message-notice-head {
  display: flex;
  align-items: center;
  gap: 0.875rem;
  padding: 1.125rem 1.25rem;
  background: linear-gradient(135deg, #f2fbf8, #ffffff);
  border-bottom: 0.0625rem solid #e6efec;
}

.message-notice-head > span {
  width: 2.625rem;
  height: 2.625rem;
  display: grid;
  place-items: center;
  border-radius: 0.875rem;
  color: #fff;
  background: linear-gradient(135deg, #26a69a, #42c7b8);
  box-shadow: 0 0.75rem 1.5rem rgba(38, 166, 154, 0.24);
}

.message-notice-head small,
.message-notice-meta,
.message-notice-footer span {
  color: #71808d;
  font-size: 0.8125rem;
}

.message-notice-head h3 {
  margin: 0.25rem 0 0;
  color: #10242f;
  font-size: 1.125rem;
}

.message-notice-content {
  padding: 1.25rem;
  color: #21343d;
  line-height: 1.75;
}

.message-notice-meta {
  margin-bottom: 0.875rem;
}

.message-rich-text {
  color: #21343d;
  word-break: break-word;
}

.message-rich-text :deep(p) {
  margin: 0 0 0.875rem;
}

.message-rich-text :deep(img) {
  max-width: 100%;
  border-radius: 0.75rem;
}

.message-notice-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.75rem;
  padding: 0.875rem 1.25rem;
  background: #fbfcfd;
  border-top: 0.0625rem solid #e9eff2;
}

@media (max-width: 47.5rem) {
  .message-head,
  .message-head-actions,
  .message-control-card {
    align-items: stretch;
    flex-direction: column;
  }

  .message-head-actions,
  .message-head-actions .el-button,
  .message-search {
    width: 100%;
  }

  .message-stat-grid {
    grid-template-columns: 1fr;
  }

  .message-item {
    grid-template-columns: 0.625rem minmax(0, 1fr);
  }

  .message-actions {
    grid-column: 2;
    justify-self: start;
    flex-wrap: wrap;
  }

  .message-title-row,
  .message-notice-footer {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
