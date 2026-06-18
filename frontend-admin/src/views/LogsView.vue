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
        <el-input v-model="filters.keyword" placeholder="搜索操作人 / 模块 / 接口" clearable />
        <el-select v-model="filters.operationType" clearable placeholder="筛选类型">
          <el-option v-for="type in operationTypes" :key="type" :label="type" :value="type" />
        </el-select>
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
        <el-table-column prop="operatorName" label="操作人" min-width="120" />
        <el-table-column prop="moduleName" label="模块" min-width="140" />
        <el-table-column prop="operationType" label="类型" min-width="120" />
        <el-table-column prop="requestUri" label="接口" min-width="220" show-overflow-tooltip />
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
        <el-descriptions-item label="操作人">{{ currentLog.operatorName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="模块">{{ currentLog.moduleName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ currentLog.operationType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="接口">{{ currentLog.requestUri || '-' }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ currentLog.ipAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="内容">{{ currentLog.operationContent || currentLog.description || '-' }}</el-descriptions-item>
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
const filters = reactive({ keyword: '', operationType: undefined as string | undefined })
const pagination = reactive({ page: 1, pageSize: 10 })

const operationTypes = computed(() => [...new Set(logs.value.map((item) => item.operationType).filter(Boolean))])
const filteredLogs = computed(() => logs.value.filter((item) => {
  const keyword = filters.keyword.trim().toLowerCase()
  const matchKeyword = !keyword || item.operatorName?.toLowerCase().includes(keyword) || item.moduleName?.toLowerCase().includes(keyword) || item.requestUri?.toLowerCase().includes(keyword)
  const matchType = !filters.operationType || item.operationType === filters.operationType
  return matchKeyword && matchType
}))
const dangerCount = computed(() => logs.value.filter((item) => `${item.operationType || ''}`.toLowerCase().includes('delete')).length)
const moduleCount = computed(() => new Set(logs.value.map((item) => item.moduleName)).size)
const pagedLogs = computed(() => filteredLogs.value.slice((pagination.page - 1) * pagination.pageSize, pagination.page * pagination.pageSize))

async function loadLogs() { logs.value = await request.get('/api/admin/logs'); pagination.page = 1 }
function resetFilters() { filters.keyword = ''; filters.operationType = undefined }
function openDetail(row: any) { currentLog.value = row; detailVisible.value = true }
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
