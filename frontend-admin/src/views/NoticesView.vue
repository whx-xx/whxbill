<template>
  <div class="admin-page">
    <div class="admin-page-head">
      <div>
        <span class="admin-page-kicker">内容运营</span>
        <h2>公告管理</h2>
        <p>编辑公告标题、发布状态和富文本正文。公告从草稿变为发布时，会同步生成用户消息并实时推送。</p>
      </div>
    </div>

    <div class="admin-stats">
      <div class="admin-stat"><div class="admin-stat-label">公告总数</div><div class="admin-stat-value">{{ notices.length }}</div></div>
      <div class="admin-stat"><div class="admin-stat-label">已发布</div><div class="admin-stat-value">{{ notices.filter((item) => item.publishStatus === 1).length }}</div></div>
      <div class="admin-stat"><div class="admin-stat-label">草稿</div><div class="admin-stat-value">{{ notices.filter((item) => item.publishStatus === 0).length }}</div></div>
    </div>

    <div class="admin-split">
      <div class="admin-card">
        <div class="admin-toolbar">
          <div class="admin-toolbar-left">
            <h3 class="admin-card-title">{{ form.id ? '编辑公告' : '新建公告' }}</h3>
            <span class="admin-muted">{{ form.publishStatus === 1 ? '发布后用户会在消息中心收到提醒' : '草稿仅管理员可见' }}</span>
          </div>
          <div class="admin-toolbar-right">
            <el-button @click="resetForm">新建</el-button>
            <el-button type="primary" :icon="Check" @click="saveNotice">保存公告</el-button>
          </div>
        </div>
        <div class="admin-card-inner">
          <el-form :model="form" label-position="top">
            <el-form-item label="公告标题"><el-input v-model="form.title" placeholder="请输入公告标题" /></el-form-item>
            <el-form-item label="发布状态">
              <el-radio-group v-model="form.publishStatus">
                <el-radio :value="1">发布</el-radio>
                <el-radio :value="0">草稿</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-form>
          <div ref="editorRef" style="height: 17.5rem; margin-bottom: 1.25rem"></div>
          <div class="notice-admin-preview">
            <span>用户端摘要预览</span>
            <strong>{{ form.title || '公告标题' }}</strong>
            <p>{{ previewText || '公告正文会以摘要形式进入消息中心，用户可在消息中心查看完整内容。' }}</p>
          </div>
        </div>
      </div>

      <div class="admin-card admin-table-card">
        <div class="admin-toolbar">
          <div class="admin-toolbar-left">
            <h3 class="admin-card-title">公告列表</h3>
            <span class="admin-muted">{{ filteredNotices.length }} 条</span>
          </div>
        </div>
        <div class="admin-filter-bar" style="grid-template-columns: minmax(0, 1fr) 9.375rem auto">
          <el-input v-model="filters.keyword" placeholder="搜索标题" clearable />
          <el-select v-model="filters.publishStatus" clearable placeholder="发布状态">
            <el-option label="发布" :value="1" />
            <el-option label="草稿" :value="0" />
          </el-select>
          <el-button @click="resetFilters">重置</el-button>
        </div>
        <el-table :data="pagedNotices" stripe height="520">
          <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-switch v-model="row.publishStatus" :active-value="1" :inactive-value="0" active-text="发布" inactive-text="草稿" inline-prompt @change="quickSaveNotice(row)" />
            </template>
          </el-table-column>
          <el-table-column label="更新时间" width="160">
            <template #default="{ row }">{{ formatTime(row.updatedTime || row.createdTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="editNotice(row)">编辑</el-button>
              <el-button link type="danger" @click="removeNotice(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="admin-table-footer">
          <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.pageSize" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50]" :total="filteredNotices.length" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import Quill from 'quill'
import 'quill/dist/quill.snow.css'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check } from '@element-plus/icons-vue'
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import request from '@/utils/request'

const editorRef = ref<HTMLDivElement>()
const notices = ref<any[]>([])
const editorInstance = ref<Quill>()
const filters = reactive({ keyword: '', publishStatus: undefined as number | undefined })
const pagination = reactive({ page: 1, pageSize: 10 })
const emptyForm = () => ({ id: undefined as number | undefined, title: '', content: '', coverUrl: '', publishStatus: 1 })
const form = reactive(emptyForm())

const filteredNotices = computed(() => notices.value.filter((item) => {
  const keyword = filters.keyword.trim().toLowerCase()
  const matchKeyword = !keyword || item.title?.toLowerCase().includes(keyword)
  const matchStatus = filters.publishStatus === undefined || item.publishStatus === filters.publishStatus
  return matchKeyword && matchStatus
}))
const pagedNotices = computed(() => filteredNotices.value.slice((pagination.page - 1) * pagination.pageSize, pagination.page * pagination.pageSize))
const previewText = computed(() => plainText(form.content).slice(0, 90))

async function loadNotices() { notices.value = await request.get('/api/admin/notices') }
function resetFilters() { filters.keyword = ''; filters.publishStatus = undefined }
function resetForm() { Object.assign(form, emptyForm()); editorInstance.value?.setText('') }
function editNotice(row: any) {
  Object.assign(form, { ...emptyForm(), ...row })
  nextTick(() => { if (editorInstance.value) editorInstance.value.root.innerHTML = row.content || '' })
}
async function saveNotice() {
  form.content = editorInstance.value?.root.innerHTML || ''
  if (!form.title.trim()) {
    ElMessage.warning('请输入公告标题')
    return
  }
  if (!plainText(form.content)) {
    ElMessage.warning('请输入公告内容')
    return
  }
  const savedNotice = form.id
    ? await request.put(`/api/admin/notices/${form.id}`, form)
    : await request.post('/api/admin/notices', form)
  upsertNotice(savedNotice)
  ElMessage.success(form.publishStatus === 1 ? '公告已保存并通知用户' : '公告草稿已保存')
  resetForm()
  await loadNotices()
}
async function quickSaveNotice(row: any) { await request.put(`/api/admin/notices/${row.id}`, row); ElMessage.success('状态已更新'); await loadNotices() }
async function removeNotice(noticeId: number) {
  await ElMessageBox.confirm('删除后不可恢复，是否继续？', '删除公告', { type: 'warning' })
  await request.delete(`/api/admin/notices/${noticeId}`)
  ElMessage.success('公告已删除')
  await loadNotices()
}
watch(filters, () => { pagination.page = 1 })
onMounted(async () => {
  if (editorRef.value) {
    editorInstance.value = new Quill(editorRef.value, { theme: 'snow' })
    editorInstance.value.on('text-change', () => {
      form.content = editorInstance.value?.root.innerHTML || ''
    })
  }
  resetForm()
  await loadNotices()
})

function plainText(html?: string) {
  const container = document.createElement('div')
  container.innerHTML = html || ''
  return container.textContent?.replace(/\s+/g, ' ').trim() || ''
}

function formatTime(value?: string) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

function upsertNotice(notice: any) {
  if (!notice?.id) return
  const index = notices.value.findIndex((item) => item.id === notice.id)
  if (index >= 0) {
    notices.value.splice(index, 1, notice)
  } else {
    notices.value.unshift(notice)
  }
  pagination.page = 1
}
</script>

<style scoped lang="stylus">
.notice-admin-preview {
  padding: 0.875rem;
  border-radius: 0.625rem;
  border: 0.0625rem solid #dfeceb;
  background: #f7fbfa;
}

.notice-admin-preview span {
  color: #718591;
  font-size: 0.75rem;
  font-weight: 800;
}

.notice-admin-preview strong {
  display: block;
  margin-top: 0.375rem;
  color: #132933;
}

.notice-admin-preview p {
  margin: 0.375rem 0 0;
  color: #6d808c;
  font-size: 0.8125rem;
  line-height: 1.6;
}
</style>
