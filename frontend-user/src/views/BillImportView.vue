<template>
  <div class="import-shell">
    <section class="import-toolbar">
      <div class="import-file-card">
        <el-icon><DocumentChecked /></el-icon>
        <div>
          <h2>{{ preview?.fileName || '微信支付账单流水文件' }}</h2>
          <p>
            来源：微信账单
            <span v-if="preview"> · 共 {{ preview.total }} 条 · 已选 {{ selectedRows.length }} 条</span>
            <span v-if="missingAccounts.length"> · 待创建账户 {{ missingAccounts.length }} 个</span>
          </p>
        </div>
      </div>
      <div class="import-toolbar-right">
        <el-select v-model="targetBookId" class="import-book-select" placeholder="选择账本" @change="handleBookChange">
          <el-option v-for="book in books" :key="book.id" :label="book.bookName" :value="book.id" />
        </el-select>
        <el-upload
          :auto-upload="false"
          :show-file-list="false"
          accept=".xlsx,.xls"
          :on-change="handleFileChange"
        >
          <el-button :icon="UploadFilled" type="primary" plain :loading="loading.preview">选择Excel</el-button>
        </el-upload>
        <el-button :disabled="!rows.length" @click="toggleAll">{{ isAllSelected ? '取消全选' : '全选' }}</el-button>
        <el-button :disabled="!selectedRows.length" type="danger" plain @click="removeSelectedRows">删除所选</el-button>
        <el-button :disabled="!selectedRows.length" type="primary" :loading="loading.importing" @click="importSelected">
          导入账单
        </el-button>
      </div>
    </section>

    <section v-if="!preview" class="import-empty ledger-panel">
      <el-upload
        drag
        :auto-upload="false"
        :show-file-list="false"
        accept=".xlsx,.xls"
        :on-change="handleFileChange"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖入或选择微信导出的 Excel 账单</div>
        <template #tip>
          <div class="el-upload__tip">支持微信支付账单明细列表，会自动识别交易时间、收支、金额、支付方式和备注。</div>
        </template>
      </el-upload>
    </section>

    <section v-else class="import-workbench">
      <aside class="ledger-panel import-raw-panel">
        <div class="import-panel-head">
          <h3>EXCEL 原始数据</h3>
          <el-tag size="small" type="info">{{ rows.length }} 行</el-tag>
        </div>
        <div class="import-raw-list">
          <button
            v-for="row in rows"
            :key="row.rowNo"
            type="button"
            class="import-raw-row"
            :class="{ active: activeRow?.rowNo === row.rowNo, invalid: !row.valid, warning: row.warningMessage && row.valid }"
            @click="activeRow = row"
          >
            <el-checkbox v-model="row.selected" :disabled="!row.valid" @click.stop />
            <span>{{ row.rowNo }}</span>
            <p>{{ row.rawText }}</p>
          </button>
        </div>
      </aside>

      <main class="ledger-panel import-result-panel">
        <div class="import-panel-head">
          <h3>识别后账单数据</h3>
          <div class="import-stat-strip">
            <span>收入 <strong class="income">¥{{ money(preview.incomeTotal) }}</strong></span>
            <span>支出 <strong class="expense">¥{{ money(preview.expenseTotal) }}</strong></span>
            <span>匹配 <strong>{{ preview.matched }}/{{ preview.total }}</strong></span>
          </div>
        </div>
        <div class="import-bill-list">
          <button
            v-for="row in rows"
            :key="row.rowNo"
            type="button"
            class="import-bill-row"
            :class="{ active: activeRow?.rowNo === row.rowNo, invalid: !row.valid }"
            @click="activeRow = row"
          >
            <el-icon class="import-bill-icon"><component :is="row.billType === 'INCOME' ? Money : Tickets" /></el-icon>
            <div class="import-bill-copy">
              <strong>{{ row.categoryName }}</strong>
              <span>{{ row.billTime || row.billDate }} · {{ row.accountName }} · {{ row.merchantName }}</span>
              <small v-if="row.accountMissing">导入时将自动创建账户：{{ row.accountName }}</small>
              <small v-else-if="row.warningMessage" class="warning">{{ row.warningMessage }}</small>
              <small v-else-if="!row.valid">{{ row.errorMessage }}</small>
            </div>
            <em :class="row.billType === 'INCOME' ? 'income' : 'expense'">
              {{ row.billType === 'INCOME' ? '+' : '-' }}{{ money(row.amount) }}
            </em>
          </button>
        </div>
      </main>

      <aside class="ledger-panel import-detail-panel">
        <template v-if="activeRow">
          <div class="import-detail-hero" :class="activeRow.billType === 'INCOME' ? 'income' : 'expense'">
            <el-icon><component :is="activeRow.billType === 'INCOME' ? Money : Tickets" /></el-icon>
            <div>
              <strong>{{ activeRow.categoryName }}</strong>
              <span>{{ activeRow.billTime || activeRow.billDate }}</span>
            </div>
            <em>{{ activeRow.billType === 'INCOME' ? '+' : '-' }}{{ money(activeRow.amount) }}</em>
          </div>

          <el-form label-position="top" class="import-detail-form">
            <el-form-item label="是否导入">
              <el-switch v-model="activeRow.selected" :disabled="!activeRow.valid" />
            </el-form-item>
            <el-form-item label="账单类型">
              <el-select v-model="activeRow.billType" style="width:100%" @change="syncRowCategory(activeRow)">
                <el-option label="支出" value="EXPENSE" />
                <el-option label="收入" value="INCOME" />
              </el-select>
            </el-form-item>
            <el-form-item label="账单分类">
              <el-select v-model="activeRow.categoryId" style="width:100%" @change="syncRowCategoryName(activeRow)">
                <el-option
                  v-for="category in categoriesByType(activeRow.billType)"
                  :key="category.id"
                  :label="category.categoryName"
                  :value="category.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="收支账户">
              <el-select v-model="activeRow.accountId" style="width:100%" @change="syncRowAccountName(activeRow)">
                <el-option
                  v-if="activeRow.accountMissing"
                  :label="`${activeRow.accountName}（导入时自动创建）`"
                  :value="undefined"
                />
                <el-option v-for="account in accounts" :key="account.id" :label="account.accountName" :value="account.id" />
              </el-select>
            </el-form-item>
            <div class="import-form-grid">
              <el-form-item label="金额">
                <el-input-number v-model="activeRow.amount" :min="0.01" :precision="2" style="width:100%" />
              </el-form-item>
              <el-form-item label="账单日期">
                <el-date-picker v-model="activeRow.billDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
              </el-form-item>
            </div>
            <el-form-item label="商户来源">
              <el-input v-model="activeRow.merchantName" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="activeRow.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-form>

          <div class="import-original-card">
            <div class="import-original-head">
              <strong>原始数据</strong>
              <span>微信账单</span>
            </div>
            <div v-for="(item, index) in activeRow.rawColumns" :key="index">
              <span>列{{ index + 1 }}</span>
              <strong>{{ item || '/' }}</strong>
            </div>
          </div>
        </template>
        <el-empty v-else description="请选择一条账单" />
      </aside>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { DocumentChecked, Money, Tickets, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { useBookContext } from '@/composables/useBookContext'
import { readImportWorkspace, writeImportWorkspace } from '@/utils/importWorkspace'

const { books, currentBookId, loadBooks } = useBookContext()
const targetBookId = ref<number>()
const preview = ref<any>()
const rows = ref<any[]>([])
const activeRow = ref<any>()
const accounts = ref<any[]>([])
const categories = ref<any[]>([])
const loading = ref({ preview: false, importing: false })

const selectedRows = computed(() => rows.value.filter((row) => row.selected && row.valid))
const isAllSelected = computed(() => rows.value.length > 0 && rows.value.every((row) => row.selected || !row.valid))
const missingAccounts = computed(() =>
  Array.from(new Set(selectedRows.value.filter((row) => row.accountMissing).map((row) => row.accountName).filter(Boolean)))
)

function money(value: number | string) {
  return Number(value || 0).toFixed(2)
}

function categoriesByType(type: string) {
  return categories.value.filter((item) => item.categoryType === type)
}

async function loadReferenceData() {
  if (!targetBookId.value) return
  const [accountList, expenseCategories, incomeCategories] = await Promise.all([
    request.get('/api/accounts', { params: { bookId: targetBookId.value } }),
    request.get('/api/categories', { params: { bookId: targetBookId.value, categoryType: 'EXPENSE' } }),
    request.get('/api/categories', { params: { bookId: targetBookId.value, categoryType: 'INCOME' } })
  ])
  accounts.value = accountList
  categories.value = [...expenseCategories, ...incomeCategories]
}

async function handleBookChange() {
  await loadReferenceData()
  remapRowsForBook()
}

async function handleFileChange(file: any) {
  if (!targetBookId.value) {
    ElMessage.warning('请先选择账本')
    return
  }
  loading.value.preview = true
  try {
    await loadReferenceData()
    const formData = new FormData()
    formData.append('bookId', String(targetBookId.value))
    formData.append('file', file.raw)
    preview.value = await request.post('/api/bills/import/preview', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    rows.value = preview.value.rows || []
    activeRow.value = rows.value[0]
    persistImportWorkspace()
    ElMessage.success(`成功识别 ${preview.value.total} 条账单`)
  } finally {
    loading.value.preview = false
  }
}

function toggleAll() {
  const next = !isAllSelected.value
  rows.value.forEach((row) => {
    if (row.valid) row.selected = next
  })
}

function syncRowCategory(row: any) {
  const first = categoriesByType(row.billType)[0]
  row.categoryId = first?.id
  row.categoryName = first?.categoryName || '未匹配分类'
  row.valid = Boolean(row.categoryId && row.accountName && row.amount && row.billDate)
}

function syncRowCategoryName(row: any) {
  row.categoryName = categories.value.find((item) => item.id === row.categoryId)?.categoryName || '未匹配分类'
  row.valid = Boolean(row.categoryId && row.accountName && row.amount && row.billDate)
}

function syncRowAccountName(row: any) {
  const account = accounts.value.find((item) => item.id === row.accountId)
  row.accountName = account?.accountName || row.accountName || '未知账户'
  row.accountMissing = !account
  row.valid = Boolean(row.categoryId && row.accountName && row.amount && row.billDate)
}

function remapRowsForBook() {
  const book = books.value.find((item) => item.id === targetBookId.value)
  rows.value.forEach((row) => {
    row.bookId = targetBookId.value
    row.bookName = book?.bookName || row.bookName
    const account = accounts.value.find((item) => item.accountName === row.accountName)
    row.accountId = account?.id
    row.accountMissing = !account
    const category = categories.value.find((item) =>
      item.categoryType === row.billType && item.categoryName === row.categoryName
    )
    if (category) {
      row.categoryId = category.id
    } else if (!categories.value.some((item) => item.id === row.categoryId && item.categoryType === row.billType)) {
      syncRowCategory(row)
    }
    row.valid = Boolean(row.categoryId && row.accountName && row.amount && row.billDate)
    row.selected = row.valid && row.selected !== false
  })
  preview.value && (preview.value.matched = rows.value.filter((row) => row.valid).length)
}

async function removeSelectedRows() {
  if (!selectedRows.value.length) return
  await ElMessageBox.confirm(`确认删除已选的 ${selectedRows.value.length} 条识别记录？删除后不会导入。`, '删除识别记录', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
  const removingRows = [...selectedRows.value]
  rows.value = rows.value.filter((row) => !removingRows.includes(row))
  activeRow.value = rows.value[0]
  refreshPreviewSummary()
  persistImportWorkspace()
}

function refreshPreviewSummary() {
  if (!preview.value) return
  preview.value.total = rows.value.length
  preview.value.matched = rows.value.filter((row) => row.valid).length
  preview.value.incomeTotal = rows.value
    .filter((row) => row.billType === 'INCOME')
    .reduce((sum, row) => sum + Number(row.amount || 0), 0)
  preview.value.expenseTotal = rows.value
    .filter((row) => row.billType === 'EXPENSE')
    .reduce((sum, row) => sum + Number(row.amount || 0), 0)
}

function persistImportWorkspace() {
  writeImportWorkspace({
    targetBookId: targetBookId.value,
    preview: preview.value,
    rows: rows.value,
    activeRowNo: activeRow.value?.rowNo
  })
}

function restoreImportWorkspace() {
  const data = readImportWorkspace()
  if (!data) return
  if (data.targetBookId) {
    targetBookId.value = data.targetBookId
  }
  preview.value = data.preview
  rows.value = data.rows || []
  activeRow.value = rows.value.find((row) => row.rowNo === data.activeRowNo) || rows.value[0]
}

async function importSelected() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请选择要导入的账单')
    return
  }
  if (missingAccounts.value.length) {
    const accountTags = missingAccounts.value.map((name) => `<span class="import-confirm-tag">${name}</span>`).join('')
    await ElMessageBox.confirm(
      `<div class="import-confirm">
        <p>共选择 <strong>${selectedRows.value.length}</strong> 条账单，是否确认导入？</p>
        <div class="import-confirm-warning">
          <strong>以下账户本地不存在，导入时将自动创建：</strong>
          <div>${accountTags}</div>
        </div>
      </div>`,
      '确认导入',
      {
        confirmButtonText: '确认导入',
        cancelButtonText: '取消',
        type: 'warning',
        dangerouslyUseHTMLString: true
      }
    )
  } else {
    await ElMessageBox.confirm(`共选择 ${selectedRows.value.length} 条账单，是否确认导入？`, '确认导入', {
      confirmButtonText: '确认导入',
      cancelButtonText: '取消',
      type: 'info'
    })
  }
  const importingRows = [...selectedRows.value]
  loading.value.importing = true
  try {
    const count = await request.post('/api/bills/import', { rows: importingRows })
    ElMessage.success(`已导入 ${count} 条账单`)
    await loadReferenceData()
    rows.value = rows.value.filter((row) => !importingRows.includes(row))
    refreshPreviewSummary()
    activeRow.value = rows.value[0]
    persistImportWorkspace()
  } finally {
    loading.value.importing = false
  }
}

watch(currentBookId, (value) => {
  if (!preview.value) {
    targetBookId.value = value
  }
}, { immediate: true })

watch([preview, rows, activeRow, targetBookId], () => {
  persistImportWorkspace()
}, { deep: true })

onMounted(async () => {
  restoreImportWorkspace()
  await loadBooks()
  targetBookId.value = targetBookId.value || currentBookId.value || books.value[0]?.id
  await loadReferenceData()
  if (rows.value.length) {
    remapRowsForBook()
  }
})
</script>

<style scoped lang="stylus">
.import-shell {
  display: grid;
  gap: 0.875rem;
}

.import-toolbar,
.import-workbench,
.import-file-card,
.import-toolbar-right,
.import-panel-head,
.import-stat-strip,
.import-detail-hero {
  display: flex;
  align-items: center;
}

.import-toolbar {
  justify-content: space-between;
  gap: 0.875rem;
  background: #effaf7;
  border: 0.0625rem solid #d9eee8;
  border-radius: 0.875rem;
  padding: 1rem 1.125rem;
}

.import-file-card {
  gap: 0.75rem;
  min-width: 0;
}

.import-file-card > .el-icon {
  width: 2.625rem;
  height: 2.625rem;
  border-radius: 0.75rem;
  display: grid;
  place-items: center;
  background: #28aa91;
  color: #fff;
  font-size: 1.375rem;
  flex: 0 0 auto;
}

.import-file-card h2 {
  margin: 0;
  font-size: 1rem;
  color: #17252e;
}

.import-file-card p {
  margin: 0.375rem 0 0;
  color: #7b8c96;
  font-size: 0.8125rem;
}

.import-toolbar-right {
  gap: 0.625rem;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.import-book-select {
  width: 10rem;
}

.import-empty {
  padding: 2.625rem;
}

.import-workbench {
  align-items: stretch;
  display: grid;
  grid-template-columns: minmax(22.5rem, 0.9fr) minmax(26.25rem, 1fr) 22.5rem;
  gap: 0.875rem;
  min-height: calc(100vh - 13.125rem);
}

.import-raw-panel,
.import-result-panel,
.import-detail-panel {
  padding: 1rem;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.import-panel-head {
  justify-content: space-between;
  gap: 0.75rem;
  padding-bottom: 0.75rem;
  border-bottom: 0.0625rem solid #edf1f4;
}

.import-panel-head h3 {
  margin: 0;
  font-size: 0.9375rem;
  color: #17252e;
}

.import-stat-strip {
  gap: 0.625rem;
  color: #7b8c96;
  font-size: 0.75rem;
}

.import-stat-strip strong {
  color: #17252e;
}

.import-stat-strip .income,
.import-bill-row em.income,
.import-detail-hero.income em {
  color: #1c9f78;
}

.import-stat-strip .expense,
.import-bill-row em.expense,
.import-detail-hero.expense em {
  color: #ee3f46;
}

.import-raw-list,
.import-bill-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  scrollbar-width: none;
  padding-top: 0.625rem;
}

.import-raw-list::-webkit-scrollbar,
.import-bill-list::-webkit-scrollbar {
  display: none;
}

.import-raw-row,
.import-bill-row {
  width: 100%;
  border: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.import-raw-row {
  display: grid;
  grid-template-columns: 1.75rem 1.75rem minmax(0, 1fr);
  gap: 0.5rem;
  align-items: center;
  padding: 0.625rem 0.5rem;
  border-radius: 0.625rem;
}

.import-raw-row.active,
.import-bill-row.active {
  background: #eef8f5;
}

.import-raw-row.invalid,
.import-bill-row.invalid {
  opacity: 0.58;
}

.import-raw-row > span {
  height: 1.5rem;
  border-radius: 0.5rem;
  display: grid;
  place-items: center;
  background: #edf1f4;
  color: #7b8c96;
  font-weight: 800;
  font-size: 0.75rem;
}

.import-raw-row p {
  margin: 0;
  color: #526776;
  font-size: 0.75rem;
  line-height: 1.7;
}

.import-bill-row {
  display: grid;
  grid-template-columns: 2.625rem minmax(0, 1fr) auto;
  gap: 0.75rem;
  align-items: center;
  padding: 0.75rem 0.625rem;
  border-radius: 0.75rem;
}

.import-bill-icon {
  width: 2.375rem;
  height: 2.375rem;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: #f3f6f8;
  color: #17252e;
  font-size: 1.25rem;
}

.import-bill-copy {
  min-width: 0;
}

.import-bill-copy strong,
.import-detail-hero strong {
  display: block;
  color: #17252e;
  font-weight: 800;
}

.import-bill-copy span,
.import-bill-copy small,
.import-detail-hero span {
  display: block;
  margin-top: 0.25rem;
  color: #8c99a8;
  font-size: 0.75rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.import-bill-copy small {
  color: #e84f57;
}

.import-bill-copy small.warning {
  color: #d98500;
}

.import-bill-row em,
.import-detail-hero em {
  font-style: normal;
  font-weight: 900;
  font-variant-numeric: tabular-nums;
}

.import-detail-hero {
  gap: 0.75rem;
  padding: 1.125rem;
  border-radius: 0.875rem;
  background: #31a88f;
  color: #fff;
}

.import-detail-hero.expense {
  background: #3aa08b;
}

.import-detail-hero > .el-icon {
  width: 2.625rem;
  height: 2.625rem;
  border-radius: 0.75rem;
  background: rgba(255, 255, 255, 0.2);
  font-size: 1.5rem;
}

.import-detail-hero strong,
.import-detail-hero span,
.import-detail-hero em {
  color: #fff !important;
}

.import-detail-hero em {
  margin-left: auto;
  font-size: 1.5rem;
}

.import-detail-form {
  padding-top: 0.875rem;
}

.import-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.625rem;
}

.import-original-card {
  margin-top: auto;
  background: #f7f9fb;
  border-radius: 0.75rem;
  padding: 0.75rem 0.875rem;
}

.import-original-head,
.import-original-card > div {
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
}

.import-original-card > div {
  padding: 0.4375rem 0;
  border-bottom: 0.0625rem solid #edf1f4;
  color: #7b8c96;
  font-size: 0.75rem;
}

.import-original-card > div:last-child {
  border-bottom: 0;
}

.import-original-card strong {
  color: #526776;
  text-align: right;
  overflow-wrap: anywhere;
}

@media (max-width: 87.5rem) {
  .import-workbench {
    grid-template-columns: 1fr 1fr;
  }

  .import-detail-panel {
    grid-column: 1 / -1;
  }
}
</style>
