<template>
  <div class="admin-page">
    <div class="admin-page-head">
      <div>
        <span class="admin-page-kicker">审计追踪</span>
        <h2>操作日志</h2>
        <p>追踪后台关键操作，支持按操作人、模块、类型和接口快速检索。</p>
      </div>
    </div>

    <div class="admin-stats">
      <div class="admin-stat"><div class="admin-stat-label">日志总数</div><div class="admin-stat-value">{{ logs.length }}</div></div>
      <div class="admin-stat"><div class="admin-stat-label">操作模块</div><div class="admin-stat-value">{{ moduleCount }}</div></div>
      <div class="admin-stat"><div class="admin-stat-label">删除类操作</div><div class="admin-stat-value">{{ dangerCount }}</div></div>
    </div>

    <div class="admin-card">
      <div class="admin-filter-bar">
        <el-input v-model="filters.keyword" placeholder="搜索操作人 / 模块 / 接口 / 内容" clearable @keyup.enter="loadLogs" @clear="loadLogs" />
        <el-select v-model="filters.moduleName" clearable placeholder="筛选模块" @change="loadLogs" @clear="loadLogs">
          <el-option v-for="module in moduleNames" :key="module" :label="module" :value="module" />
        </el-select>
        <el-select v-model="filters.operationType" clearable placeholder="筛选类型" @change="loadLogs" @clear="loadLogs">
          <el-option v-for="type in operationTypes" :key="type" :label="type" :value="type" />
        </el-select>
        <el-date-picker
          v-model="filters.timeRange"
          type="datetimerange"
          value-format="YYYY-MM-DD HH:mm:ss"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          range-separator="至"
          clearable
          @change="loadLogs"
        />
        <div class="admin-toolbar-right">
          <el-button @click="resetFilters">重置</el-button>
          <el-button :icon="Refresh" @click="loadLogs">刷新</el-button>
        </div>
      </div>
    </div>

    <div class="admin-card admin-table-card">
      <div class="admin-toolbar">
        <div class="admin-toolbar-left">
          <h3 class="admin-card-title">日志列表</h3>
          <span class="admin-muted">当前 {{ filteredLogs.length }} 条结果</span>
        </div>
        <div class="admin-toolbar-right">
          <el-button type="danger" plain :icon="Delete" @click="clearLogs">清空日志</el-button>
        </div>
      </div>
      <el-table :data="pagedLogs" stripe height="520">
        <el-table-column label="操作时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdTime) }}</template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" min-width="120" />
        <el-table-column prop="moduleName" label="模块" min-width="140" />
        <el-table-column prop="operationType" label="类型" min-width="120" />
        <el-table-column prop="requestMethod" label="方法" min-width="90" />
        <el-table-column prop="requestUri" label="接口" min-width="220" show-overflow-tooltip />
        <el-table-column prop="operationContent" label="具体操作" min-width="260" show-overflow-tooltip />
        <el-table-column prop="ipAddress" label="IP" min-width="130" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="danger" @click="removeLog(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="admin-table-footer">
        <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.pageSize" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50]" :total="filteredLogs.length" />
      </div>
    </div>

    <el-drawer v-model="detailVisible" title="日志详情" size="26.25rem">
      <el-descriptions v-if="currentLog" :column="1" border>
        <el-descriptions-item label="操作时间">{{ formatDateTime(currentLog.createdTime) }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ currentLog.operatorName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="模块">{{ currentLog.moduleName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ currentLog.operationType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="请求方法">{{ currentLog.requestMethod || '-' }}</el-descriptions-item>
        <el-descriptions-item label="接口">{{ currentLog.requestUri || '-' }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ currentLog.ipAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="具体操作">{{ currentLog.operationContent || currentLog.description || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Refresh } from '@element-plus/icons-vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import request from '@/utils/request'

const logs = ref<any[]>([])
const currentLog = ref<any>()
const detailVisible = ref(false)
const filters = reactive({
  keyword: '',
  moduleName: undefined as string | undefined,
  operationType: undefined as string | undefined,
  timeRange: [] as string[]
})
const pagination = reactive({ page: 1, pageSize: 10 })

const moduleNames = computed(() => [...new Set(logs.value.map((item) => item.moduleName).filter(Boolean))])
const operationTypes = computed(() => [...new Set(logs.value.map((item) => item.operationType).filter(Boolean))])
const filteredLogs = computed(() => logs.value.filter((item) => {
  const keyword = filters.keyword.trim().toLowerCase()
  const content = `${item.operatorName || ''} ${item.moduleName || ''} ${item.operationType || ''} ${item.requestMethod || ''} ${item.requestUri || ''} ${item.ipAddress || ''} ${item.operationContent || ''}`.toLowerCase()
  const matchKeyword = !keyword || content.includes(keyword)
  const matchModule = !filters.moduleName || item.moduleName === filters.moduleName
  const matchType = !filters.operationType || item.operationType === filters.operationType
  return matchKeyword && matchModule && matchType
}))
const dangerCount = computed(() => logs.value.filter((item) => `${item.operationType || ''}`.toLowerCase().includes('delete')).length)
const moduleCount = computed(() => new Set(logs.value.map((item) => item.moduleName)).size)
const pagedLogs = computed(() => filteredLogs.value.slice((pagination.page - 1) * pagination.pageSize, pagination.page * pagination.pageSize))

async function loadLogs() {
  logs.value = await request.get('/api/admin/logs', {
    params: {
      keyword: filters.keyword || undefined,
      moduleName: filters.moduleName,
      operationType: filters.operationType,
      startTime: filters.timeRange?.[0],
      endTime: filters.timeRange?.[1]
    }
  })
  pagination.page = 1
}
function resetFilters() {
  filters.keyword = ''
  filters.moduleName = undefined
  filters.operationType = undefined
  filters.timeRange = []
  loadLogs()
}
function openDetail(row: any) { currentLog.value = row; detailVisible.value = true }
function formatDateTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}
async function removeLog(logId: number) {
  await ElMessageBox.confirm('删除该日志记录？', '删除日志', { type: 'warning' })
  await request.delete(`/api/admin/logs/${logId}`)
  ElMessage.success('日志已删除')
  await loadLogs()
}
async function clearLogs() {
  await ElMessageBox.confirm('清空后无法恢复，是否继续？', '清空日志', { type: 'warning' })
  await request.delete('/api/admin/logs/clear')
  ElMessage.success('日志已清空')
  await loadLogs()
}
watch(filters, () => { pagination.page = 1 })
onMounted(loadLogs)
</script>
