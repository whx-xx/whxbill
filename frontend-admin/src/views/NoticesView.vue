<template>
  <div class="admin-page">
    <div class="admin-page-head">
      <div>
        <span class="admin-page-kicker">内容运营</span>
        <h2>公告管理</h2>
        <p>编辑公告标题、封面、发布状态和富文本正文。</p>
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
            <span class="admin-muted">富文本、封面和发布状态集中处理</span>
          </div>
          <div class="admin-toolbar-right">
            <el-button @click="resetForm">新建</el-button>
            <el-button type="primary" :icon="Check" @click="saveNotice">保存公告</el-button>
          </div>
        </div>
        <div class="admin-card-inner">
          <el-form :model="form" label-position="top">
            <el-form-item label="公告标题"><el-input v-model="form.title" placeholder="请输入公告标题" /></el-form-item>
            <el-form-item label="封面地址">
              <el-input v-model="form.coverUrl" placeholder="可粘贴图片地址，也可上传封面" />
              <el-upload style="margin-top: 8px" :show-file-list="false" :auto-upload="false" accept="image/*" :on-change="uploadCover">
                <el-button :icon="Upload">上传封面</el-button>
              </el-upload>
              <img v-if="form.coverUrl" :src="form.coverUrl" alt="cover" style="width: 180px; height: 96px; object-fit: cover; border-radius: 8px; margin-top: 12px" />
            </el-form-item>
            <el-form-item label="发布状态">
              <el-radio-group v-model="form.publishStatus">
                <el-radio :value="1">发布</el-radio>
                <el-radio :value="0">草稿</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-form>
          <div ref="editorRef" style="height: 280px; margin-bottom: 20px"></div>
        </div>
      </div>

      <div class="admin-card admin-table-card">
        <div class="admin-toolbar">
          <div class="admin-toolbar-left">
            <h3 class="admin-card-title">公告列表</h3>
            <span class="admin-muted">{{ filteredNotices.length }} 条</span>
          </div>
        </div>
        <div class="admin-filter-bar" style="grid-template-columns: minmax(0, 1fr) 150px auto">
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
import { Check, Upload } from '@element-plus/icons-vue'
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

async function loadNotices() { notices.value = await request.get('/api/admin/notices') }
async function uploadCover(file: any) {
  const formData = new FormData()
  formData.append('file', file.raw)
  const result = await request.post('/api/files/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
  form.coverUrl = result.fileUrl
  ElMessage.success('封面上传成功')
}
function resetFilters() { filters.keyword = ''; filters.publishStatus = undefined }
function resetForm() { Object.assign(form, emptyForm()); editorInstance.value?.setText('') }
function editNotice(row: any) {
  Object.assign(form, { ...emptyForm(), ...row })
  nextTick(() => { if (editorInstance.value) editorInstance.value.root.innerHTML = row.content || '' })
}
async function saveNotice() {
  form.content = editorInstance.value?.root.innerHTML || ''
  await request.post('/api/admin/notices', form)
  ElMessage.success('公告已保存')
  resetForm()
  await loadNotices()
}
async function quickSaveNotice(row: any) { await request.post('/api/admin/notices', row); ElMessage.success('状态已更新'); await loadNotices() }
async function removeNotice(noticeId: number) {
  await ElMessageBox.confirm('删除后不可恢复，是否继续？', '删除公告', { type: 'warning' })
  await request.delete(`/api/admin/notices/${noticeId}`)
  ElMessage.success('公告已删除')
  await loadNotices()
}
watch(filters, () => { pagination.page = 1 })
onMounted(async () => {
  if (editorRef.value) editorInstance.value = new Quill(editorRef.value, { theme: 'snow' })
  resetForm()
  await loadNotices()
})
</script>
