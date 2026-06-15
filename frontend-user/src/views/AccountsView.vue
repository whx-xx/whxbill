<template>
  <div class="account-manage-shell">
    <section class="account-page-head">
      <div>
        <div class="manage-section-eyebrow">Accounts</div>
        <h2>账户管理</h2>
        <p>管理当前账本下的现金、银行卡、微信、支付宝等收支账户，统一维护余额、颜色和排序。</p>
      </div>
      <el-button :icon="Plus" type="primary" @click="openCreateDialog">新增账户</el-button>
    </section>

    <section class="account-stat-grid">
      <div class="account-stat-card">
        <span>账户总数</span>
        <strong>{{ accounts.length }}</strong>
        <small>当前筛选 {{ filteredAccounts.length }} 个</small>
      </div>
      <div class="account-stat-card balance">
        <span>资产合计</span>
        <strong>{{ formatCurrency(totalBalance) }}</strong>
        <small>按当前筛选统计</small>
      </div>
      <div class="account-stat-card">
        <span>账户类型</span>
        <strong>{{ accountTypeCount }}</strong>
        <small>{{ activeFilterText }}</small>
      </div>
      <div class="account-stat-card selected">
        <span>已选择</span>
        <strong>{{ selectedIds.length }}</strong>
        <small>可批量删除</small>
      </div>
    </section>

    <section class="account-control-card">
      <div class="account-filter-row">
        <el-input
          v-model="filters.keyword"
          clearable
          class="account-search"
          placeholder="搜索账户名称"
          :prefix-icon="Search"
        />
        <el-segmented v-model="filters.accountType" :options="typeOptions" class="account-type-tabs" />
        <div class="account-control-actions">
          <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
          <el-button
            :disabled="!selectedIds.length"
            :icon="Delete"
            :loading="loading.deleting"
            type="danger"
            plain
            @click="removeSelectedAccounts"
          >
            批量删除
          </el-button>
          <el-button :icon="Plus" type="primary" @click="openCreateDialog">新增账户</el-button>
        </div>
      </div>
    </section>

    <section class="account-workbench">
      <aside class="account-type-panel">
        <div class="account-panel-head">
          <div>
            <h3>账户类型</h3>
            <span>点击类型快速筛选</span>
          </div>
        </div>
        <button
          v-for="item in typeSummary"
          :key="item.value"
          type="button"
          class="account-type-card"
          :class="{ active: filters.accountType === item.value }"
          @click="filters.accountType = item.value"
        >
          <span class="account-type-icon" :style="{ color: item.color, background: item.softColor }">
            <el-icon><component :is="item.icon" /></el-icon>
          </span>
          <span class="account-type-main">
            <strong>{{ item.label }}</strong>
            <small>{{ item.count }} 个账户</small>
          </span>
          <em>{{ formatCurrency(item.balance) }}</em>
        </button>
      </aside>

      <main class="account-table-panel">
        <div class="account-panel-head table-head">
          <div>
            <h3>账户列表</h3>
            <span>已选 {{ selectedIds.length }} 条，支持分页和批量操作</span>
          </div>
        </div>

        <el-table
          ref="tableRef"
          v-loading="loading.list"
          :data="pagedAccounts"
          row-key="id"
          empty-text="暂无账户数据"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="48" />
          <el-table-column label="账户名称" min-width="240">
            <template #default="{ row }">
              <div class="account-name-cell">
                <span class="account-row-icon" :style="{ background: `${row.colorTag || typeMeta(row.accountType).color}18`, color: row.colorTag || typeMeta(row.accountType).color }">
                  <el-icon><component :is="typeMeta(row.accountType).icon" /></el-icon>
                </span>
                <div>
                  <strong>{{ row.accountName }}</strong>
                  <small>排序 {{ formatSortOrder(row.sortOrder) }}</small>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="账户类型" width="120">
            <template #default="{ row }">
              <el-tag effect="plain">{{ typeMeta(row.accountType).label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="余额" width="170" align="right">
            <template #default="{ row }">
              <span class="account-amount">{{ formatCurrency(row.balance) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="标记色" width="150">
            <template #default="{ row }">
              <div class="manage-color-cell">
                <span class="manage-color-dot" :style="{ background: row.colorTag || typeMeta(row.accountType).color }"></span>
                {{ row.colorTag || typeMeta(row.accountType).color }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <div class="manage-action-links">
                <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
                <el-button link type="danger" @click="removeAccount(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="manage-pagination">
          <el-pagination
            v-model:current-page="pageState.pageNum"
            v-model:page-size="pageState.pageSize"
            :page-sizes="[10, 20, 50]"
            background
            layout="total, sizes, prev, pager, next"
            :total="filteredAccounts.length"
            @current-change="clearSelection"
            @size-change="handlePageSizeChange"
          />
        </div>
      </main>
    </section>

    <el-dialog
      v-model="dialogVisible"
      class="account-dialog"
      :title="form.id ? '编辑账户' : '新增账户'"
      width="min(640px, calc(100vw - 24px))"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="account-form-grid">
          <el-form-item label="账户名称" prop="accountName">
            <el-input v-model.trim="form.accountName" maxlength="24" show-word-limit placeholder="例如：微信钱包、招商银行卡" />
          </el-form-item>
          <el-form-item label="账户类型" prop="accountType">
            <el-select v-model="form.accountType" style="width: 100%">
              <el-option v-for="item in accountTypes" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="余额" prop="balance">
            <el-input-number v-model="form.balance" :precision="2" :min="0" :max="999999999" style="width: 100%" />
          </el-form-item>
          <el-form-item label="排序" prop="sortOrder">
            <el-input-number v-model="form.sortOrder" :min="0" :max="999" style="width: 100%" />
          </el-form-item>
          <el-form-item label="标记色" prop="colorTag">
            <div class="account-color-field">
              <el-color-picker v-model="form.colorTag" />
              <span class="manage-color-dot" :style="{ background: form.colorTag }"></span>
              <span>{{ form.colorTag }}</span>
            </div>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading.saving" @click="saveAccount">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ChatDotRound, Coin, CreditCard, Delete, Money, Plus, Refresh, Search, Wallet } from '@element-plus/icons-vue'
import type { FormInstance, FormRules, TableInstance } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useBookContext } from '@/composables/useBookContext'
import request from '@/utils/request'

interface AccountItem {
  id: number
  bookId?: number
  accountName: string
  accountType: string
  balance?: number | string
  colorTag?: string
  sortOrder?: number
}

interface AccountForm {
  id?: number
  bookId?: number
  accountName: string
  accountType: string
  balance: number
  colorTag: string
  sortOrder: number
}

const accounts = ref<AccountItem[]>([])
const selectedIds = ref<number[]>([])
const dialogVisible = ref(false)
const tableRef = ref<TableInstance>()
const formRef = ref<FormInstance>()
const { currentBookId, loadBooks } = useBookContext()

const accountTypes = [
  { value: 'CASH', label: '现金', color: '#26A69A', softColor: '#E8F7F5', icon: Money },
  { value: 'BANK', label: '银行卡', color: '#4F8EF7', softColor: '#EEF5FF', icon: CreditCard },
  { value: 'ALIPAY', label: '支付宝', color: '#1677FF', softColor: '#EDF5FF', icon: Wallet },
  { value: 'WECHAT', label: '微信', color: '#43C66A', softColor: '#ECFAF0', icon: ChatDotRound },
  { value: 'OTHER', label: '其他', color: '#7B8794', softColor: '#F1F4F7', icon: Coin }
]

const typeOptions = computed(() => [
  { label: '全部', value: '' },
  ...accountTypes.map((item) => ({ label: item.label, value: item.value }))
])

const pageState = reactive({
  pageNum: 1,
  pageSize: 10
})

const loading = reactive({
  list: false,
  saving: false,
  deleting: false
})

const filters = reactive({
  accountType: '',
  keyword: ''
})

const emptyForm = (): AccountForm => {
  const type = filters.accountType || 'CASH'
  return {
    id: undefined,
    bookId: currentBookId.value,
    accountName: '',
    accountType: type,
    balance: 0,
    colorTag: typeMeta(type).color,
    sortOrder: nextSortOrder(type)
  }
}

const form = reactive<AccountForm>(emptyForm())

const rules: FormRules<AccountForm> = {
  accountName: [
    { required: true, message: '请输入账户名称', trigger: 'blur' },
    { min: 2, max: 24, message: '账户名称长度为 2-24 个字符', trigger: 'blur' },
    { validator: validateUniqueName, trigger: 'blur' }
  ],
  accountType: [{ required: true, message: '请选择账户类型', trigger: 'change' }],
  balance: [{ required: true, message: '请输入账户余额', trigger: 'change' }],
  colorTag: [{ required: true, message: '请选择标记色', trigger: 'change' }],
  sortOrder: [{ required: true, message: '请设置排序', trigger: 'change' }]
}

const sortedAccounts = computed(() =>
  [...accounts.value].sort((left, right) =>
    left.accountType.localeCompare(right.accountType) || Number(left.sortOrder || 0) - Number(right.sortOrder || 0)
  )
)

const filteredAccounts = computed(() =>
  sortedAccounts.value.filter((item) => {
    const matchType = !filters.accountType || item.accountType === filters.accountType
    const keyword = filters.keyword.trim().toLowerCase()
    const matchKeyword = !keyword || item.accountName.toLowerCase().includes(keyword)
    return matchType && matchKeyword
  })
)

const pagedAccounts = computed(() => {
  const start = (pageState.pageNum - 1) * pageState.pageSize
  return filteredAccounts.value.slice(start, start + pageState.pageSize)
})

const totalBalance = computed(() =>
  filteredAccounts.value.reduce((sum, item) => sum + Number(item.balance || 0), 0)
)

const accountTypeCount = computed(() => new Set(accounts.value.map((item) => item.accountType)).size)
const activeFilterText = computed(() => filters.accountType ? typeMeta(filters.accountType).label : '全部类型')

const typeSummary = computed(() =>
  accountTypes.map((type) => {
    const rows = accounts.value.filter((item) => item.accountType === type.value)
    return {
      ...type,
      count: rows.length,
      balance: rows.reduce((sum, item) => sum + Number(item.balance || 0), 0)
    }
  })
)

function typeMeta(type?: string) {
  return accountTypes.find((item) => item.value === type) || accountTypes[4]
}

function nextSortOrder(type: string) {
  return accounts.value.filter((item) => item.accountType === type).length
}

function formatCurrency(value: number | string) {
  return `¥${Number(value || 0).toFixed(2)}`
}

function formatSortOrder(sortOrder?: number) {
  return typeof sortOrder === 'number' ? `第 ${sortOrder + 1} 位` : '-'
}

function validateUniqueName(_rule: unknown, value: string, callback: (error?: Error) => void) {
  const name = value?.trim()
  if (!name) {
    callback()
    return
  }
  const duplicated = accounts.value.some((item) => item.id !== form.id && item.accountName === name)
  callback(duplicated ? new Error('已存在同名账户') : undefined)
}

function clearSelection() {
  selectedIds.value = []
  tableRef.value?.clearSelection()
}

function handleSelectionChange(selection: AccountItem[]) {
  selectedIds.value = selection.map((item) => item.id)
}

function handlePageSizeChange() {
  pageState.pageNum = 1
  clearSelection()
}

function resetFilters() {
  filters.accountType = ''
  filters.keyword = ''
  pageState.pageNum = 1
  clearSelection()
}

function syncAccountDefaults() {
  form.bookId = currentBookId.value
  if (!form.id) {
    if (!form.colorTag || form.colorTag === typeMeta(previousType.value).color) {
      form.colorTag = typeMeta(form.accountType).color
    }
    form.sortOrder = nextSortOrder(form.accountType)
  }
}

const previousType = ref('CASH')

async function openCreateDialog() {
  Object.assign(form, emptyForm())
  previousType.value = form.accountType
  dialogVisible.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

async function openEditDialog(row: AccountItem) {
  Object.assign(form, {
    ...emptyForm(),
    ...row,
    balance: Number(row.balance || 0),
    colorTag: row.colorTag || typeMeta(row.accountType).color,
    sortOrder: Number(row.sortOrder || 0)
  })
  previousType.value = form.accountType
  dialogVisible.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

async function loadAccounts() {
  if (!currentBookId.value || loading.list) return
  loading.list = true
  try {
    accounts.value = await request.get('/api/accounts', { params: { bookId: currentBookId.value } })
    if ((pageState.pageNum - 1) * pageState.pageSize >= filteredAccounts.value.length && pageState.pageNum > 1) {
      pageState.pageNum = Math.max(1, pageState.pageNum - 1)
    }
    clearSelection()
  } finally {
    loading.list = false
  }
}

async function saveAccount() {
  if (loading.saving) return
  syncAccountDefaults()
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.saving = true
  try {
    await request.post('/api/accounts', {
      ...form,
      accountName: form.accountName.trim()
    })
    ElMessage.success(form.id ? '账户已更新' : '账户已新增')
    dialogVisible.value = false
    await loadAccounts()
  } finally {
    loading.saving = false
  }
}

async function removeAccount(row: AccountItem) {
  if (loading.deleting) return
  await ElMessageBox.confirm(`确认删除账户「${row.accountName}」吗？删除后不可恢复。`, '删除账户', { type: 'warning' })
  loading.deleting = true
  try {
    await request.delete(`/api/accounts/${row.id}`)
    ElMessage.success('账户已删除')
    await loadAccounts()
  } finally {
    loading.deleting = false
  }
}

async function removeSelectedAccounts() {
  if (loading.deleting || !selectedIds.value.length) return
  await ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 个账户吗？`, '批量删除账户', {
    type: 'warning'
  })
  loading.deleting = true
  try {
    await Promise.all(selectedIds.value.map((id) => request.delete(`/api/accounts/${id}`)))
    ElMessage.success('选中账户已删除')
    await loadAccounts()
  } finally {
    loading.deleting = false
  }
}

watch(
  () => form.accountType,
  (value, oldValue) => {
    previousType.value = oldValue || previousType.value
    syncAccountDefaults()
    previousType.value = value
  }
)

watch(
  () => [filters.accountType, filters.keyword],
  () => {
    pageState.pageNum = 1
    clearSelection()
  }
)

watch(currentBookId, async (value, oldValue) => {
  if (!value || value === oldValue) return
  pageState.pageNum = 1
  await loadAccounts()
  Object.assign(form, emptyForm())
}, { immediate: false })

onMounted(async () => {
  await loadBooks()
  await loadAccounts()
})
</script>

<style scoped>
.account-manage-shell {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.account-page-head,
.account-control-card,
.account-type-panel,
.account-table-panel,
.account-stat-card {
  background: #fff;
  border: 1px solid #e5ebf0;
  border-radius: 8px;
}

.account-page-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 20px;
}

.account-page-head h2,
.account-panel-head h3 {
  margin: 0;
  color: #10242f;
}

.account-page-head h2 {
  font-size: 24px;
}

.account-page-head p,
.account-panel-head span,
.account-stat-card span,
.account-stat-card small,
.account-name-cell small,
.account-type-main small {
  color: #7d8d9a;
  font-size: 13px;
}

.account-page-head p {
  margin: 8px 0 0;
}

.account-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.account-stat-card {
  min-height: 96px;
  padding: 16px;
  border-left: 4px solid #d8e4ea;
  display: grid;
  align-content: center;
  gap: 6px;
}

.account-stat-card strong {
  font-size: 25px;
  line-height: 1;
  color: #10242f;
}

.account-stat-card.balance {
  border-left-color: #1fa77a;
}

.account-stat-card.selected {
  border-left-color: #238be6;
}

.account-control-card {
  padding: 14px 16px;
}

.account-filter-row,
.account-control-actions,
.account-panel-head,
.account-type-card,
.account-name-cell,
.account-color-field {
  display: flex;
  align-items: center;
}

.account-filter-row {
  gap: 12px;
  flex-wrap: wrap;
}

.account-search {
  width: min(360px, 100%);
}

.account-control-actions {
  gap: 10px;
  margin-left: auto;
}

.account-workbench {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.account-type-panel,
.account-table-panel {
  min-width: 0;
}

.account-type-panel {
  padding: 16px;
  position: sticky;
  top: 82px;
}

.account-panel-head {
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.account-panel-head h3 {
  font-size: 16px;
}

.account-type-card {
  width: 100%;
  gap: 10px;
  padding: 11px 10px;
  border: 1px solid #edf1f4;
  border-radius: 8px;
  background: #fbfcfd;
  color: #10242f;
  cursor: pointer;
  text-align: left;
}

.account-type-card + .account-type-card {
  margin-top: 8px;
}

.account-type-card:hover,
.account-type-card.active {
  border-color: #bfe5df;
  background: #f4fbfa;
}

.account-type-icon,
.account-row-icon {
  display: inline-grid;
  place-items: center;
  flex: 0 0 auto;
}

.account-type-icon {
  width: 38px;
  height: 38px;
  border-radius: 10px;
}

.account-row-icon {
  width: 34px;
  height: 34px;
  border-radius: 9px;
}

.account-type-main {
  min-width: 0;
  flex: 1;
  display: grid;
  gap: 3px;
}

.account-type-card em {
  color: #10242f;
  font-style: normal;
  font-weight: 800;
  white-space: nowrap;
}

.account-table-panel {
  overflow: hidden;
}

.account-table-panel .table-head {
  padding: 16px 18px 0;
}

.account-table-panel :deep(.el-table) {
  --el-table-border-color: #edf1f5;
  --el-table-header-bg-color: #f6f8fb;
  --el-table-row-hover-bg-color: #f8fbfb;
}

.account-table-panel :deep(.el-table th.el-table__cell) {
  color: #667985;
  font-weight: 700;
}

.account-table-panel :deep(.el-table td.el-table__cell),
.account-table-panel :deep(.el-table th.el-table__cell) {
  padding-top: 13px;
  padding-bottom: 13px;
}

.account-name-cell {
  gap: 10px;
  min-width: 0;
}

.account-name-cell > div {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.account-name-cell strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-amount {
  color: #10242f;
  font-size: 15px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.account-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.account-color-field {
  gap: 10px;
  min-height: 32px;
  color: #667985;
}

.account-dialog :deep(.el-dialog__body) {
  padding-top: 8px;
}

@media (max-width: 1180px) {
  .account-stat-grid,
  .account-workbench {
    grid-template-columns: 1fr;
  }

  .account-type-panel {
    position: static;
  }
}

@media (max-width: 760px) {
  .account-page-head,
  .account-filter-row,
  .account-control-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .account-page-head .el-button,
  .account-control-actions,
  .account-control-actions .el-button,
  .account-search {
    width: 100%;
  }

  .account-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
