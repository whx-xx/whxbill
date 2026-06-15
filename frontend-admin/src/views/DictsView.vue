<template>
  <div class="admin-page">
    <div class="admin-page-head">
      <div>
        <span class="admin-page-kicker">系统配置</span>
        <h2>字典配置</h2>
        <p>统一维护业务枚举、标签和值映射，支撑前后端一致展示。</p>
      </div>
    </div>

    <div class="admin-stats">
      <div class="admin-stat"><div class="admin-stat-label">字典条目</div><div class="admin-stat-value">{{ dicts.length }}</div></div>
      <div class="admin-stat"><div class="admin-stat-label">启用条目</div><div class="admin-stat-value">{{ dicts.filter((item) => item.status === 1).length }}</div></div>
      <div class="admin-stat"><div class="admin-stat-label">字典类型</div><div class="admin-stat-value">{{ distinctTypes }}</div></div>
    </div>

    <div class="admin-card">
      <div class="admin-filter-bar">
        <el-input v-model="filters.keyword" placeholder="搜索类型 / 标签 / 值" clearable />
        <el-select v-model="filters.status" clearable placeholder="筛选状态">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <div class="admin-toolbar-right">
          <el-button @click="resetFilters">重置</el-button>
          <el-button :icon="Refresh" @click="loadDicts">刷新</el-button>
        </div>
      </div>
    </div>

    <div class="admin-card admin-table-card">
      <div class="admin-toolbar">
        <div class="admin-toolbar-left">
          <h3 class="admin-card-title">字典列表</h3>
          <span class="admin-muted">当前 {{ filteredDicts.length }} 条结果</span>
        </div>
        <div class="admin-toolbar-right">
          <el-button type="primary" :icon="Plus" @click="openCreate">新增字典</el-button>
        </div>
      </div>
      <el-table :data="pagedDicts" stripe height="520">
        <el-table-column prop="dictType" label="类型" min-width="160" />
        <el-table-column prop="dictLabel" label="标签" min-width="160" />
        <el-table-column prop="dictValue" label="值" min-width="160" />
        <el-table-column prop="sortOrder" label="排序" width="100" />
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-switch v-model="row.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" inline-prompt @change="quickSaveDict(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="removeDict(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="admin-table-footer">
        <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.pageSize" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50]" :total="filteredDicts.length" />
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑字典' : '新增字典'" width="560px">
      <el-form :model="form" label-position="top">
        <el-form-item label="字典类型"><el-input v-model="form.dictType" placeholder="例如 bill_source" /></el-form-item>
        <el-form-item label="字典标签"><el-input v-model="form.dictLabel" /></el-form-item>
        <el-form-item label="字典值"><el-input v-model="form.dictValue" /></el-form-item>
        <div class="admin-form-grid">
          <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" style="width: 100%" /></el-form-item>
          <el-form-item label="状态">
            <el-select v-model="form.status" style="width: 100%">
              <el-option label="启用" :value="1" />
              <el-option label="停用" :value="0" />
            </el-select>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveDict">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import request from '@/utils/request'

const dicts = ref<any[]>([])
const dialogVisible = ref(false)
const filters = reactive({ keyword: '', status: undefined as number | undefined })
const pagination = reactive({ page: 1, pageSize: 10 })
const emptyForm = () => ({ id: undefined as number | undefined, dictType: '', dictLabel: '', dictValue: '', sortOrder: 0, status: 1 })
const form = reactive(emptyForm())

const distinctTypes = computed(() => new Set(dicts.value.map((item) => item.dictType)).size)
const filteredDicts = computed(() => dicts.value.filter((item) => {
  const keyword = filters.keyword.trim().toLowerCase()
  const matchKeyword = !keyword || item.dictType?.toLowerCase().includes(keyword) || item.dictLabel?.toLowerCase().includes(keyword) || item.dictValue?.toLowerCase().includes(keyword)
  const matchStatus = filters.status === undefined || item.status === filters.status
  return matchKeyword && matchStatus
}))
const pagedDicts = computed(() => filteredDicts.value.slice((pagination.page - 1) * pagination.pageSize, pagination.page * pagination.pageSize))

async function loadDicts() { dicts.value = await request.get('/api/admin/dicts') }
function resetFilters() { filters.keyword = ''; filters.status = undefined }
function openCreate() { Object.assign(form, emptyForm()); dialogVisible.value = true }
function openEdit(row: any) { Object.assign(form, { ...emptyForm(), ...row }); dialogVisible.value = true }
async function saveDict() { await request.post('/api/admin/dicts', form); ElMessage.success('字典已保存'); dialogVisible.value = false; await loadDicts() }
async function quickSaveDict(row: any) { await request.post('/api/admin/dicts', row); ElMessage.success('状态已更新'); await loadDicts() }
async function removeDict(dictId: number) {
  await ElMessageBox.confirm('删除后不可恢复，是否继续？', '删除字典', { type: 'warning' })
  await request.delete(`/api/admin/dicts/${dictId}`)
  ElMessage.success('字典已删除')
  await loadDicts()
}
watch(filters, () => { pagination.page = 1 })
onMounted(loadDicts)
</script>
