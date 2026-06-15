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
              <el-tag :type="message.readStatus === 0 ? 'primary' : 'info'" effect="plain">
                {{ message.readStatus === 0 ? '未读' : '已读' }}
              </el-tag>
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
            <el-button v-if="message.readStatus === 0" size="small" type="primary" plain @click="markRead(message.id)">标记已读</el-button>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { useNotificationStore } from '@/stores/notification'

const router = useRouter()
const notificationStore = useNotificationStore()
const messages = computed(() => notificationStore.messages)
const loading = ref(false)
const markingAll = ref(false)
const keyword = ref('')
const statusFilter = ref<'ALL' | 'UNREAD' | 'READ'>('ALL')

const statusOptions = [
  { label: '全部', value: 'ALL' },
  { label: '未读', value: 'UNREAD' },
  { label: '已读', value: 'READ' }
]

const unreadMessages = computed(() => messages.value.filter((item) => item.readStatus === 0))
const readMessages = computed(() => messages.value.filter((item) => item.readStatus !== 0))

const filteredMessages = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return messages.value.filter((item) => {
    const matchStatus =
      statusFilter.value === 'ALL'
      || (statusFilter.value === 'UNREAD' && item.readStatus === 0)
      || (statusFilter.value === 'READ' && item.readStatus !== 0)
    const matchKeyword = !q || `${item.title || ''} ${item.content || ''}`.toLowerCase().includes(q)
    return matchStatus && matchKeyword
  })
})

async function loadMessages() {
  if (loading.value) return
  loading.value = true
  try {
    await notificationStore.loadMessages()
  } finally {
    loading.value = false
  }
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

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

onMounted(loadMessages)
</script>

<style scoped>
.message-shell {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.message-head,
.message-control-card,
.message-list-card,
.message-stat-card {
  background: #fff;
  border: 1px solid #e5ebf0;
  border-radius: 8px;
}

.message-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 20px;
}

.message-head h2,
.message-list-head h3,
.message-item h4 {
  margin: 0;
  color: #10242f;
}

.message-head h2 {
  font-size: 24px;
}

.message-head p,
.message-list-head span,
.message-stat-card span,
.message-stat-card small,
.message-item p,
.message-item small {
  color: #7d8d9a;
  font-size: 13px;
}

.message-head p {
  margin: 8px 0 0;
}

.message-head-actions {
  display: flex;
  gap: 10px;
}

.message-stat-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.message-stat-card {
  min-height: 96px;
  padding: 16px;
  border-left: 4px solid #d8e4ea;
  display: grid;
  align-content: center;
  gap: 6px;
}

.message-stat-card strong {
  color: #10242f;
  font-size: 26px;
  line-height: 1;
}

.message-stat-card.unread {
  border-left-color: #238be6;
}

.message-stat-card.read {
  border-left-color: #22a879;
}

.message-control-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  flex-wrap: wrap;
}

.message-search {
  width: min(380px, 100%);
}

.message-list-card {
  padding: 16px;
}

.message-list-head {
  margin-bottom: 14px;
}

.message-list {
  display: grid;
  gap: 10px;
}

.message-item {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: start;
  padding: 14px;
  border: 1px solid #edf1f4;
  border-radius: 8px;
  background: #fbfcfd;
}

.message-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.message-item.unread {
  border-color: #cfe5ff;
  background: #f7fbff;
}

.message-dot {
  width: 8px;
  height: 8px;
  margin-top: 7px;
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
  gap: 12px;
}

.message-title-row h4 {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-item p {
  margin: 8px 0;
  line-height: 1.6;
}

@media (max-width: 760px) {
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
    grid-template-columns: 10px minmax(0, 1fr);
  }

  .message-actions {
    grid-column: 2;
    justify-self: start;
    flex-wrap: wrap;
  }
}
</style>
