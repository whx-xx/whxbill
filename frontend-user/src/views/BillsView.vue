<template>
  <div class="ledger-shell">
    <aside class="ledger-panel ledger-filter-panel">
      <div class="ledger-panel-title">
        <div>
          <span class="ledger-eyebrow">搜索筛选</span>
          <h2>账单</h2>
        </div>
      </div>

      <el-input
        v-model="filters.keyword"
        clearable
        class="ledger-search"
        placeholder="输入账单，如：午餐30、微信"
        :prefix-icon="Search"
        @keyup.enter="loadBills"
      />

      <div class="ledger-filter-block">
        <div class="ledger-filter-label">日期</div>
        <el-select v-model="filters.month" class="ledger-no-clear" :clearable="false" placeholder="账单月份">
          <el-option v-for="item in monthOptions" :key="item" :label="item" :value="item" />
        </el-select>
        <el-date-picker
          v-model="filters.dateRange"
          class="ledger-no-clear"
          type="daterange"
          start-placeholder="起始日期"
          end-placeholder="截止日期"
          value-format="YYYY-MM-DD"
          :clearable="false"
          style="width:100%"
        />
      </div>

      <div class="ledger-filter-block">
        <div class="ledger-filter-label">类型</div>
        <el-segmented
          v-model="filters.billType"
          :options="[
            { label: '全部', value: '' },
            { label: '支出', value: 'EXPENSE' },
            { label: '收入', value: 'INCOME' }
          ]"
          block
        />
      </div>

      <div class="ledger-filter-block">
        <div class="ledger-filter-label">记录方式</div>
        <el-select v-model="filters.sourceType" clearable placeholder="全部记录方式">
          <el-option label="手动记账" value="MANUAL" />
          <el-option label="自动记账" value="AUTO" />
          <el-option label="Excel导入" value="IMPORT" />
          <el-option label="OCR导入" value="OCR" />
        </el-select>
      </div>

      <div class="ledger-filter-block">
        <div class="ledger-filter-label">账户 / 分类</div>
        <el-select v-model="filters.accountId" clearable placeholder="账户">
          <el-option v-for="item in accounts" :key="item.id" :label="item.accountName" :value="item.id" />
        </el-select>
        <el-select v-model="filters.categoryId" clearable placeholder="分类">
          <el-option
            v-for="item in filterCategoryOptions"
            :key="item.id"
            :label="item.categoryName"
            :value="item.id"
          />
        </el-select>
      </div>

      <div class="ledger-filter-actions">
        <el-button @click="resetFilters">重置</el-button>
        <el-button type="primary" @click="loadBills">搜索</el-button>
      </div>
    </aside>

    <section class="ledger-panel ledger-list-panel">
      <div class="ledger-month-card">
        <div class="ledger-summary-head">
          <el-date-picker
            v-model="filters.month"
            class="ledger-month-picker"
            type="month"
            value-format="YYYY-MM"
            placeholder="全部月份"
            :prefix-icon="Calendar"
            :clearable="false"
            @change="handleMonthChange"
          />
          <div class="ledger-filter-tags">
            <el-tag v-for="tag in summaryTags" :key="tag" effect="plain" round>{{ tag }}</el-tag>
          </div>
        </div>
        <div class="ledger-summary">
          <div class="ledger-summary-item expense">
            <span>支出</span>
            <strong>{{ formatCurrency(billSummary.expense) }}</strong>
          </div>
          <div class="ledger-summary-item income">
            <span>收入</span>
            <strong>{{ formatCurrency(billSummary.income) }}</strong>
          </div>
          <div class="ledger-summary-item balance">
            <span>结余</span>
            <strong>{{ formatCurrency(billSummary.balance) }}</strong>
          </div>
        </div>
      </div>

      <div class="ledger-list-head">
        <div>
          <h3>账单流水</h3>
          <span>共 {{ bills.total }} 条，已选 {{ selectedIds.length }} 条</span>
        </div>
        <div class="ledger-list-actions">
          <el-button :disabled="!bills.records.length" @click="toggleCurrentPageSelection">
            {{ isCurrentPageSelected ? '取消全选' : '全选本页' }}
          </el-button>
          <el-button :icon="Download" :loading="loading.exporting" @click="exportBills">
            {{ loading.exporting ? `导出中 ${loading.exportProgress}%` : '导出Excel' }}
          </el-button>
          <el-button :icon="UploadFilled" @click="router.push('/bill-import')">导入Excel</el-button>
          <el-button
            :disabled="!selectedIds.length"
            :icon="Delete"
            :loading="loading.deleting"
            type="danger"
            plain
            @click="removeSelectedBills"
          >
            删除
          </el-button>
          <el-button class="ledger-add-button" :icon="Plus" type="primary" @click="openCreateDialog">新增账单</el-button>
        </div>
      </div>

      <div class="ledger-stream">
        <el-empty v-if="!bills.records.length" description="暂无账单数据" />
        <article
          v-for="row in bills.records"
          v-else
          :key="row.id"
          class="ledger-bill-item"
          :class="{ active: selectedBill?.id === row.id }"
          @click="selectBill(row)"
        >
          <el-checkbox
            :model-value="selectedIds.includes(row.id)"
            @click.stop
            @change="toggleSelected(row.id)"
          />
          <div class="ledger-bill-icon" :class="row.billType === 'EXPENSE' ? 'expense' : 'income'">
            <el-icon><component :is="categoryIconById(row.categoryId)" /></el-icon>
          </div>
          <div class="ledger-bill-main">
            <div class="ledger-bill-title">
              {{ categoryNameMap[row.categoryId] || row.merchantName || '未分类账单' }}
              <el-tag size="small" :type="sourceTypeTagType(row.sourceType)">
                {{ sourceTypeLabel(row.sourceType) }}
              </el-tag>
            </div>
            <div class="ledger-bill-meta">
              {{ formatDate(row.billDate) }} · {{ accountNameMap[row.accountId] || '未知账户' }}
              <span v-if="row.remark"> · {{ row.remark }}</span>
            </div>
          </div>
          <div class="ledger-bill-amount" :class="row.billType === 'EXPENSE' ? 'expense' : 'income'">
            {{ row.billType === 'EXPENSE' ? '-' : '+' }}{{ formatCurrency(row.amount, row.bookId) }}
          </div>
        </article>
      </div>

      <div class="ledger-pagination">
        <el-pagination
          v-model:current-page="pageState.pageNum"
          v-model:page-size="pageState.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          background
          layout="total, sizes, prev, pager, next"
          :total="bills.total"
          @current-change="loadBills"
          @size-change="handlePageSizeChange"
        />
      </div>
    </section>

    <aside class="ledger-detail-column">
      <section class="ledger-panel ledger-detail-panel">
        <div v-if="selectedBill" class="ledger-detail">
          <div class="ledger-detail-hero" :class="selectedBill.billType === 'EXPENSE' ? 'expense' : 'income'">
            <div class="ledger-detail-icon">
              <el-icon><component :is="categoryIconById(selectedBill.categoryId)" /></el-icon>
            </div>
            <div>
              <div class="ledger-detail-title">{{ categoryNameMap[selectedBill.categoryId] || '未分类' }}</div>
              <div class="ledger-detail-time">{{ formatBillDateTime(selectedBill) }}</div>
            </div>
            <strong>{{ selectedBill.billType === 'EXPENSE' ? '-' : '+' }}{{ formatCurrency(selectedBill.amount, selectedBill.bookId) }}</strong>
          </div>
          <div class="ledger-detail-rows">
            <div><span>账单分类</span><strong>{{ categoryNameMap[selectedBill.categoryId] || '未分类' }}</strong></div>
            <div><span>归属账本</span><strong>{{ bookNameMap[selectedBill.bookId] || '未知账本' }}</strong></div>
            <div><span>收支账户</span><strong>{{ accountNameMap[selectedBill.accountId] || '未知账户' }}</strong></div>
            <div><span>商户来源</span><strong>{{ selectedBill.merchantName || '未填写' }}</strong></div>
            <div><span>备注</span><strong>{{ selectedBill.remark || '暂无备注' }}</strong></div>
            <div><span>记录方式</span><strong>{{ sourceTypeLabel(selectedBill.sourceType) }}</strong></div>
          </div>
          <div class="ledger-detail-actions">
            <el-button type="primary" plain @click="openEditDialog(selectedBill)">编辑</el-button>
            <el-button type="danger" @click="removeBill(selectedBill.id)">删除</el-button>
          </div>
        </div>
        <el-empty v-else description="暂无数据" />
      </section>

      <section class="ledger-panel ledger-quick-panel">
        <div class="ledger-quick-head">
          <strong>快捷记账</strong>
          <el-button :icon="Plus" circle type="primary" @click="openCreateDialog" />
        </div>
        <el-segmented v-model="form.billType" :options="quickTypeOptions" block />
        <div class="ledger-category-grid ledger-category-primary-grid">
          <template v-for="row in quickPrimaryRows" :key="row.map((item) => item.id).join('-')">
            <template v-for="item in row" :key="item.id">
              <button
                type="button"
                :class="{ active: isQuickPrimaryActive(item) }"
                @click="selectQuickPrimaryCategory(item)"
              >
                <span><el-icon><component :is="categoryIcon(item)" /></el-icon></span>
                {{ item.categoryName }}
              </button>
            </template>
            <div
              v-if="selectedQuickRowChildren(row).length"
              class="ledger-subcategory-panel ledger-subcategory-inline"
            >
              <button
                v-for="child in selectedQuickRowChildren(row)"
                :key="child.id"
                type="button"
                :class="{ active: form.categoryId === child.id }"
                @click="form.categoryId = child.id"
              >
                <span><el-icon><component :is="categoryIcon(child)" /></el-icon></span>
                {{ child.categoryName }}
              </button>
            </div>
          </template>
        </div>
        <div class="ledger-amount-field">
          <span>{{ formCurrencyUnit }}</span>
          <el-input-number v-model="form.amount" :min="0" :precision="2" style="width:100%" />
        </div>
        <el-button type="primary" size="large" style="width:100%" :loading="loading.saving" @click="saveBill">完成</el-button>
      </section>
    </aside>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑账单' : '新增账单'" width="min(55rem, calc(100vw - 1.5rem))">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="bill-dialog-form">
        <div class="bill-dialog-intro">
          <div>
            <strong>{{ form.id ? '调整账单信息' : '录入一笔新账单' }}</strong>
            <span>先选类型和分类，再补金额、账户和来源说明，录入会更顺手。</span>
          </div>
          <el-segmented v-model="form.billType" :options="quickTypeOptions" class="bill-dialog-type-switch" />
        </div>

        <div class="bill-dialog-layout">
          <section class="bill-dialog-card bill-dialog-card-main">
            <div class="bill-dialog-card-head">
              <strong>分类选择</strong>
              <small>
                {{
                  form.categoryId
                    ? `已选：${selectedCategoryPath(form.categoryId)}`
                    : '请选择本次收支分类'
                }}
              </small>
            </div>
            <el-form-item label="分类" prop="categoryId" class="bill-dialog-category-item">
            <div class="bill-dialog-category-picker">
              <div class="bill-dialog-category-block">
                <div class="bill-dialog-category-grid" :class="{ 'is-income': form.billType === 'INCOME' }">
                  <template v-for="row in quickPrimaryRows" :key="row.map((item) => item.id).join('-')">
                    <template v-for="item in row" :key="item.id">
                      <button
                        type="button"
                        :class="{ active: isQuickPrimaryActive(item) }"
                        @click="selectQuickPrimaryCategory(item)"
                      >
                        <span><el-icon><component :is="categoryIcon(item)" /></el-icon></span>
                        {{ item.categoryName }}
                      </button>
                    </template>
                    <div
                      v-if="selectedQuickRowChildren(row).length"
                      class="bill-dialog-subcategory-panel"
                    >
                      <button
                        v-for="child in selectedQuickRowChildren(row)"
                        :key="child.id"
                        type="button"
                        :class="{ active: form.categoryId === child.id }"
                        @click="form.categoryId = child.id"
                      >
                        <span><el-icon><component :is="categoryIcon(child)" /></el-icon></span>
                        {{ child.categoryName }}
                      </button>
                    </div>
                  </template>
                </div>
              </div>
            </div>
            </el-form-item>
          </section>

          <section class="bill-dialog-card bill-dialog-card-side">
            <div class="bill-dialog-card-head">
              <strong>账单信息</strong>
              <small>补充金额、账户和日期</small>
            </div>
            <div class="bill-dialog-fields">
              <el-form-item label="归属账本" prop="bookId">
                <el-select v-model="form.bookId" style="width:100%" @visible-change="handleBookSelectVisibleChange">
                  <el-option v-for="book in books" :key="book.id" :label="book.bookName" :value="book.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="账户" prop="accountId">
                <el-select v-model="form.accountId" style="width:100%">
                  <el-option v-for="item in accounts" :key="item.id" :label="item.accountName" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item :label="`金额（${formCurrencyUnit}）`" prop="amount" class="bill-dialog-amount-field">
                <div class="ledger-amount-field">
                  <span>{{ formCurrencyUnit }}</span>
                  <el-input-number v-model="form.amount" :min="0" :precision="2" style="width:100%" />
                </div>
              </el-form-item>
              <el-form-item label="日期" prop="billTime" class="bill-dialog-date-field">
                <el-date-picker
                  v-model="form.billTime"
                  type="datetime"
                  :clearable="false"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  format="YYYY-MM-DD HH:mm:ss"
                  style="width:100%"
                />
              </el-form-item>
              <el-form-item label="商户" prop="merchantName" class="bill-dialog-field-span">
                <el-input v-model="form.merchantName" placeholder="例如 星巴克 / 工资入账" />
              </el-form-item>
              <el-form-item label="备注" prop="remark" class="bill-dialog-field-span">
                <el-input v-model="form.remark" type="textarea" :rows="4" placeholder="补充这笔账单的来源、对象或发生场景" />
              </el-form-item>
            </div>
          </section>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading.saving" @click="saveBill">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  Basketball,
  Briefcase,
  Bowl,
  Brush,
  Calendar,
  Cellphone,
  Coin,
  Coffee,
  CreditCard,
  Delete,
  Dish,
  Document,
  Download,
  Edit,
  FirstAidKit,
  Film,
  Food,
  Goblet,
  Goods,
  Help,
  House,
  Iphone,
  KnifeFork,
  Lightning,
  Money,
  Notebook,
  Phone,
  Plus,
  Present,
  Printer,
  Reading,
  Refrigerator,
  School,
  Search,
  ShoppingBag,
  Service,
  SuitcaseLine,
  Ticket,
  Tickets,
  Tools,
  UploadFilled,
  User,
  Van,
  Wallet
} from '@element-plus/icons-vue'
import type { FormInstance, FormRules, TableInstance } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useBookContext } from '@/composables/useBookContext'
import request from '@/utils/request'

const router = useRouter()

function formatLocalDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function formatLocalDateTime(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  const second = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hour}:${minute}:${second}`
}

const currentMonth = () => formatLocalDate(new Date()).slice(0, 7)
const currentDate = () => formatLocalDate(new Date())
const currentDateTime = () => formatLocalDateTime(new Date())
const monthOptions = Array.from({ length: 12 }, (_, index) => {
  const date = new Date()
  date.setMonth(date.getMonth() - index)
  return formatLocalDate(date).slice(0, 7)
})
const { books, currentBookId, loadBooks } = useBookContext()

const bills = ref<{ total: number; records: any[] }>({ total: 0, records: [] })
const billSummary = ref({ expense: 0, income: 0, balance: 0 })
const accounts = ref<any[]>([])
const categories = ref<any[]>([])
const currencyDicts = ref<any[]>([])
const selectedIds = ref<number[]>([])
const selectedBill = ref<any>()
const selectedQuickParentId = ref<number>()
const dialogVisible = ref(false)
const tableRef = ref<TableInstance>()
const formRef = ref<FormInstance>()
let filterSearchTimer: number | undefined
let suppressFilterSearch = false
const quickTypeOptions = [
  { label: '支出', value: 'EXPENSE' },
  { label: '收入', value: 'INCOME' }
]

const pageState = reactive({
  pageNum: 1,
  pageSize: 10
})
const loading = reactive({
  list: false,
  saving: false,
  deleting: false,
  exporting: false,
  exportProgress: 0
})

const filters = reactive({
  month: currentMonth(),
  billType: '',
  accountId: undefined as number | undefined,
  categoryId: undefined as number | undefined,
  sourceType: '',
  keyword: '',
  dateRange: [] as string[]
})

const emptyForm = () => ({
  id: undefined as number | undefined,
  bookId: currentBookId.value,
  accountId: accounts.value[0]?.id as number | undefined,
  categoryId: undefined as number | undefined,
  billType: (filters.billType || 'EXPENSE') as string,
  amount: 0,
  billDate: currentDate(),
  billTime: currentDateTime(),
  merchantName: '',
  remark: '',
  sourceType: 'MANUAL'
})

const form = reactive(emptyForm())

const rules: FormRules = {
  bookId: [{ required: true, message: '请选择归属账本', trigger: 'change' }],
  billType: [{ required: true, message: '请选择账单类型', trigger: 'change' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'change' }],
  accountId: [{ required: true, message: '请选择账户', trigger: 'change' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  billTime: [{ required: true, message: '请选择账单时间', trigger: 'change' }],
  merchantName: [{ required: true, message: '请输入商户或来源说明', trigger: 'blur' }]
}

const accountNameMap = computed(() =>
  Object.fromEntries(accounts.value.map((item) => [item.id, item.accountName]))
)
const bookMap = computed(() =>
  Object.fromEntries(books.value.map((item) => [item.id, item]))
)
const bookNameMap = computed(() =>
  Object.fromEntries(books.value.map((item) => [item.id, item.bookName]))
)
const categoryNameMap = computed(() =>
  Object.fromEntries(categories.value.map((item) => [item.id, item.categoryName]))
)
const categoryIconMap = computed(() =>
  Object.fromEntries(categories.value.map((item) => [item.id, categoryIcon(item)]))
)
const filterCategoryOptions = computed(() =>
  categories.value.filter((item) => !filters.billType || item.categoryType === filters.billType)
)
const availableCategories = computed(() =>
  categories.value.filter((item) => item.categoryType === form.billType)
)
const quickPrimaryCategories = computed(() => {
  if (form.billType === 'EXPENSE') {
    return availableCategories.value
      .filter((item) => !item.parentId || item.parentId === 0)
      .slice(0, 9)
  }
  return availableCategories.value.slice(0, 12)
})
const quickPrimaryRows = computed(() => {
  const rows: any[][] = []
  for (let index = 0; index < quickPrimaryCategories.value.length; index += 4) {
    rows.push(quickPrimaryCategories.value.slice(index, index + 4))
  }
  return rows
})
const quickChildCategories = computed(() =>
  availableCategories.value.filter((item) => Number(item.parentId || 0) === selectedQuickParentId.value)
)
const summaryTags = computed(() => {
  const tags: string[] = []
  const bookName = bookNameMap.value[currentBookId.value as number]
  if (bookName) tags.push(bookName)
  if (filters.billType) tags.push(filters.billType === 'EXPENSE' ? '支出' : '收入')
  if (filters.sourceType) tags.push(sourceTypeLabel(filters.sourceType))
  if (filters.accountId) tags.push(accountNameMap.value[filters.accountId])
  if (filters.categoryId) tags.push(categoryNameMap.value[filters.categoryId])
  if (filters.dateRange?.length === 2) tags.push(`${filters.dateRange[0]} 至 ${filters.dateRange[1]}`)
  if (filters.keyword.trim()) tags.push(`关键词：${filters.keyword.trim()}`)
  return tags.filter(Boolean)
})
const isCurrentPageSelected = computed(() =>
  bills.value.records.length > 0 && bills.value.records.every((item) => selectedIds.value.includes(item.id))
)
const currentBook = computed(() => bookMap.value[currentBookId.value as number])
const formBook = computed(() => bookMap.value[form.bookId as number] || currentBook.value)
const formCurrencyUnit = computed(() => currencyMeta(formBook.value?.currencyCode).unit)
const fallbackCurrencies = [
  { dictLabel: '人民币', dictValue: 'CNY', dictExtra: '{"symbol":"¥","locale":"zh-CN","fractionDigits":2}' },
  { dictLabel: '美元', dictValue: 'USD', dictExtra: '{"symbol":"$","locale":"en-US","fractionDigits":2}' },
  { dictLabel: '欧元', dictValue: 'EUR', dictExtra: '{"symbol":"€","locale":"de-DE","fractionDigits":2}' }
]

function currencyMeta(currencyCode?: string) {
  const code = String(currencyCode || 'CNY').toUpperCase()
  const item = (currencyDicts.value.length ? currencyDicts.value : fallbackCurrencies)
    .find((dict) => String(dict.dictValue || '').toUpperCase() === code)
  const extra = currencyExtra(item)
  return {
    locale: extra.locale || 'zh-CN',
    currency: code,
    unit: extra.symbol ? `${code} ${extra.symbol}` : code,
    fractionDigits: Number.isFinite(Number(extra.fractionDigits)) ? Number(extra.fractionDigits) : 2
  }
}

function formatCurrency(value: number | string, bookId?: number) {
  const book = bookId ? bookMap.value[bookId] : currentBook.value
  const meta = currencyMeta(book?.currencyCode)
  return new Intl.NumberFormat(meta.locale, {
    style: 'currency',
    currency: meta.currency,
    minimumFractionDigits: meta.fractionDigits,
    maximumFractionDigits: meta.fractionDigits
  }).format(Number(value || 0))
}

function currencyExtra(item?: any) {
  try {
    return JSON.parse(item?.dictExtra || '{}')
  } catch {
    return {}
  }
}

async function loadCurrencyDicts() {
  try {
    currencyDicts.value = await request.get('/api/user/dicts/currency')
  } catch {
    currencyDicts.value = []
  }
}

function formatDate(value?: string) {
  return value ? value.replace(/-/g, '/') : '-'
}

function formatBillDateTime(row: any) {
  const value = row?.billTime || row?.createdTime || row?.billDate
  if (!value) return '-'
  const normalized = String(value).replace('T', ' ')
  return normalized.length >= 19 ? normalized.slice(0, 19).replace(/-/g, '/') : formatDate(normalized)
}

function normalizeBillTime(row: any) {
  if (row?.billTime) {
    return String(row.billTime).replace('T', ' ').slice(0, 19)
  }
  if (row?.billDate) {
    return `${row.billDate} 00:00:00`
  }
  return currentDateTime()
}

function datePart(value?: string) {
  return value ? value.slice(0, 10) : currentDate()
}

function toBackendDateTime(value?: string) {
  return value ? value.replace(' ', 'T').slice(0, 19) : ''
}

function sourceTypeLabel(value?: string) {
  const normalized = String(value || '').trim().toUpperCase()
  if (normalized === 'AUTO' || value === '自动记账') return '自动记账'
  if (normalized === 'OCR' || value === 'OCR识别' || value === 'OCR导入') return 'OCR导入'
  if (normalized === 'IMPORT' || normalized === 'EXCEL' || value === 'Excel导入') return 'Excel导入'
  return '手动记账'
}

function sourceTypeTagType(value?: string) {
  const normalized = String(value || '').trim().toUpperCase()
  if (normalized === 'AUTO' || value === '自动记账') return 'primary'
  if (normalized === 'OCR' || value === 'OCR识别' || value === 'OCR导入') return 'warning'
  if (normalized === 'IMPORT' || normalized === 'EXCEL' || value === 'Excel导入') return 'success'
  return 'info'
}

function categoryIcon(item: any) {
  const key = String(item?.icon || item?.categoryName || '').toLowerCase()
  if (key.includes('food') || key.includes('餐') || key.includes('饭')) return KnifeFork
  if (key.includes('coffee') || key.includes('饮料') || key.includes('酒水') || key.includes('早餐')) return Coffee
  if (key.includes('dish') || key.includes('晚餐')) return Dish
  if (key.includes('bowl') || key.includes('午餐')) return Bowl
  if (key.includes('bus') || key.includes('交通') || key.includes('出行')) return Van
  if (key.includes('train') || key.includes('飞机') || key.includes('火车') || key.includes('打车') || key.includes('停车') || key.includes('加油')) return Van
  if (key.includes('tools') || key.includes('修车') || key.includes('装修')) return Tools
  if (key.includes('home') || key.includes('居家') || key.includes('房')) return House
  if (key.includes('water') || key.includes('水费')) return Coffee
  if (key.includes('lightning') || key.includes('电费') || key.includes('燃气')) return Lightning
  if (key.includes('phone') || key.includes('话费') || key.includes('宽带')) return Phone
  if (key.includes('shopping') || key.includes('购物') || key.includes('日用')) return ShoppingBag
  if (key.includes('printer') || key.includes('办公')) return Printer
  if (key.includes('pet') || key.includes('宠物')) return Service
  if (key.includes('refrigerator') || key.includes('电器')) return Refrigerator
  if (key.includes('iphone') || key.includes('数码')) return Iphone
  if (key.includes('cellphone') || key.includes('充值')) return Cellphone
  if (key.includes('brush') || key.includes('美妆') || key.includes('清洁')) return Brush
  if (key.includes('game') || key.includes('娱乐') || key.includes('休闲')) return Basketball
  if (key.includes('film') || key.includes('电影') || key.includes('唱歌')) return Film
  if (key.includes('goblet') || key.includes('酒吧')) return Goblet
  if (key.includes('service') || key.includes('按摩') || key.includes('足浴')) return Service
  if (key.includes('gift') || key.includes('人情') || key.includes('礼')) return Present
  if (key.includes('red') || key.includes('红包')) return Present
  if (key.includes('health') || key.includes('医疗') || key.includes('健康')) return FirstAidKit
  if (key.includes('hospital') || key.includes('医院') || key.includes('药')) return FirstAidKit
  if (key.includes('culture') || key.includes('教育') || key.includes('学习')) return Reading
  if (key.includes('edit') || key.includes('考试') || key.includes('培训')) return Edit
  if (key.includes('notebook') || key.includes('书报') || key.includes('杂志')) return Notebook
  if (key.includes('school') || key.includes('学费')) return School
  if (key.includes('reimburse') || key.includes('报销')) return Document
  if (key.includes('subsidy') || key.includes('补贴')) return Money
  if (key.includes('second') || key.includes('闲置') || key.includes('二手')) return SuitcaseLine
  if (key.includes('salary') || key.includes('工资') || key.includes('奖金') || key.includes('兼职') || key.includes('外快')) return Money
  if (key.includes('borrow') || key.includes('借入')) return Wallet
  if (key.includes('lottery') || key.includes('中奖')) return Coin
  if (key.includes('profit') || key.includes('盈利')) return Coin
  if (key.includes('travel') || key.includes('差旅') || key.includes('旅游')) return SuitcaseLine
  if (key.includes('card') || key.includes('账户') || key.includes('银行')) return CreditCard
  if (key.includes('ticket') || key.includes('票')) return Ticket
  if (key.includes('coin') || key.includes('理财')) return Coin
  if (key.includes('work') || key.includes('办公')) return Briefcase
  if (key.includes('user') || key.includes('长辈')) return User
  if (key.includes('help') || key.includes('慈善') || key.includes('捐助')) return Help
  if (key.includes('document') || key.includes('罚款') || key.includes('赔偿')) return Document
  if (key.includes('goods')) return Goods
  if (key.includes('其他') || key.includes('未分类')) return Food
  return Wallet
}

function categoryIconById(categoryId: number) {
  return categoryIconMap.value[categoryId] || Wallet
}

function selectedCategoryPath(categoryId?: number) {
  if (!categoryId) return '未选择'
  const current = categories.value.find((item) => item.id === categoryId)
  if (!current) return categoryNameMap.value[categoryId] || '当前分类'
  const parentId = Number(current.parentId || 0)
  if (!parentId) {
    return current.categoryName
  }
  const parent = categories.value.find((item) => item.id === parentId)
  return parent ? `${parent.categoryName} / ${current.categoryName}` : current.categoryName
}

function syncQuickParent() {
  if (form.billType !== 'EXPENSE') {
    selectedQuickParentId.value = undefined
    return
  }
  const current = availableCategories.value.find((item) => item.id === form.categoryId)
  if (current?.parentId) {
    selectedQuickParentId.value = Number(current.parentId)
    return
  }
  if (current && (!current.parentId || current.parentId === 0)) {
    selectedQuickParentId.value = current.id
    return
  }
  if (!selectedQuickParentId.value || !quickPrimaryCategories.value.some((item) => item.id === selectedQuickParentId.value)) {
    selectedQuickParentId.value = quickPrimaryCategories.value[0]?.id
  }
}

function isQuickPrimaryActive(item: any) {
  if (form.billType !== 'EXPENSE') {
    return form.categoryId === item.id
  }
  return selectedQuickParentId.value === item.id
}

function quickChildrenByParent(parentId: number) {
  return availableCategories.value.filter((item) => Number(item.parentId || 0) === parentId)
}

function selectedQuickRowChildren(row: any[]) {
  if (form.billType !== 'EXPENSE' || !row.some((item) => item.id === selectedQuickParentId.value)) {
    return []
  }
  return quickChildCategories.value.length
    ? quickChildCategories.value
    : row.filter((item) => item.id === selectedQuickParentId.value)
}

function selectQuickPrimaryCategory(item: any) {
  if (form.billType !== 'EXPENSE') {
    form.categoryId = item.id
    return
  }
  selectedQuickParentId.value = item.id
  form.categoryId = quickChildCategories.value[0]?.id || item.id
}

function syncFormDefaults() {
  if (!form.bookId) {
    form.bookId = currentBookId.value
  }
  if ((!form.accountId || !accounts.value.some((item) => item.id === form.accountId)) && accounts.value.length) {
    form.accountId = accounts.value[0].id
  }
  syncQuickParent()
  const firstCategory = form.billType === 'EXPENSE'
    ? (quickChildCategories.value[0] || quickPrimaryCategories.value[0])
    : availableCategories.value[0]
  if (!availableCategories.value.some((item) => item.id === form.categoryId)) {
    form.categoryId = firstCategory?.id
  }
}

function resetForm() {
  Object.assign(form, emptyForm())
  syncFormDefaults()
}

function resetFilters() {
  suppressFilterSearch = true
  filters.month = currentMonth()
  filters.billType = ''
  filters.accountId = undefined
  filters.categoryId = undefined
  filters.sourceType = ''
  filters.keyword = ''
  filters.dateRange = []
  pageState.pageNum = 1
  suppressFilterSearch = false
  loadBills()
}

function triggerFilterSearch() {
  if (suppressFilterSearch) return
  pageState.pageNum = 1
  if (filterSearchTimer) {
    window.clearTimeout(filterSearchTimer)
  }
  filterSearchTimer = window.setTimeout(() => {
    loadBills()
  }, 180)
}

function handleSelectionChange(selection: any[]) {
  selectedIds.value = selection.map((item) => item.id)
}

function toggleSelected(id: number) {
  selectedIds.value = selectedIds.value.includes(id)
    ? selectedIds.value.filter((item) => item !== id)
    : [...selectedIds.value, id]
}

function toggleCurrentPageSelection() {
  const currentIds = bills.value.records.map((item) => item.id)
  if (isCurrentPageSelected.value) {
    selectedIds.value = selectedIds.value.filter((id) => !currentIds.includes(id))
    return
  }
  selectedIds.value = Array.from(new Set([...selectedIds.value, ...currentIds]))
}

function selectBill(row: any) {
  selectedBill.value = selectedBill.value?.id === row.id ? undefined : row
}

function handlePageSizeChange() {
  pageState.pageNum = 1
  loadBills()
}

function handleMonthChange() {
  pageState.pageNum = 1
  filters.dateRange = []
}

async function openCreateDialog() {
  await loadBooks(true)
  resetForm()
  dialogVisible.value = true
}

function openEditDialog(row: any) {
  Object.assign(form, {
    ...emptyForm(),
    ...row,
    amount: Number(row.amount || 0),
    billDate: row.billDate,
    billTime: normalizeBillTime(row)
  })
  syncFormDefaults()
  dialogVisible.value = true
}

async function loadReferenceData() {
  if (!currentBookId.value) return
  const [accountList, expenseCategories, incomeCategories] = await Promise.all([
    loadAccountsForBook(currentBookId.value),
    request.get('/api/categories', { params: { bookId: currentBookId.value, categoryType: 'EXPENSE' } }),
    request.get('/api/categories', { params: { bookId: currentBookId.value, categoryType: 'INCOME' } })
  ])
  accounts.value = accountList
  categories.value = [...expenseCategories, ...incomeCategories]
  syncFormDefaults()
}

async function handleBookSelectVisibleChange(visible: boolean) {
  if (visible) {
    await loadBooks(true)
  }
}

async function loadAccountsForBook(bookId?: number) {
  if (!bookId) return []
  return request.get('/api/accounts', { params: { bookId } })
}

function billQueryParams() {
  const [startDate, endDate] = filters.dateRange || []
  return {
    bookId: currentBookId.value,
    month: filters.month || undefined,
    billType: filters.billType || undefined,
    sourceType: filters.sourceType || undefined,
    accountId: filters.accountId,
    categoryId: filters.categoryId,
    keyword: filters.keyword.trim() || undefined,
    startDate,
    endDate
  }
}

async function loadBills() {
  if (!currentBookId.value) return
  loading.list = true
  try {
    const params = billQueryParams()
    const [pageResult, summaryResult] = await Promise.all([
      request.get('/api/bills', {
        params: {
          pageNum: pageState.pageNum,
          pageSize: pageState.pageSize,
          ...params
        }
      }),
      request.get('/api/bills/summary', { params })
    ])
    bills.value = pageResult
    billSummary.value = {
      expense: Number(summaryResult?.expense || 0),
      income: Number(summaryResult?.income || 0),
      balance: Number(summaryResult?.balance || 0)
    }
    selectedIds.value = []
    selectedBill.value = bills.value.records[0]
    tableRef.value?.clearSelection()
  } finally {
    loading.list = false
  }
}

async function exportBills() {
  if (!currentBookId.value || loading.exporting) return
  loading.exporting = true
  loading.exportProgress = 0
  try {
    const blob = await request.download('/api/bills/export', {
      params: billQueryParams(),
      onDownloadProgress: (event) => {
        if (event.total) {
          loading.exportProgress = Math.min(100, Math.round((event.loaded / event.total) * 100))
        }
      }
    })
    loading.exportProgress = 100
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `账单流水-${filters.month || currentMonth()}.xlsx`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
    ElMessage.success('账单流水已导出')
  } finally {
    loading.exporting = false
  }
}

async function saveBill() {
  if (loading.saving) return
  syncFormDefaults()
  const quickSave = !dialogVisible.value
  if (dialogVisible.value) {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return
  }
  if (!form.accountId || !form.categoryId || Number(form.amount) <= 0 || !form.billTime) {
    ElMessage.warning('请先补全账户、分类、金额和时间')
    return
  }

  loading.saving = true
  try {
    const payload = { ...form }
    payload.billDate = datePart(payload.billTime as string)
    payload.billTime = toBackendDateTime(payload.billTime as string)
    if (quickSave) {
      const categoryId = payload.categoryId as number
      payload.id = undefined
      payload.merchantName = payload.merchantName || categoryNameMap.value[categoryId] || '快捷记账'
      payload.remark = payload.remark || '快捷记账'
    }
    if (payload.id) {
      await request.put(`/api/bills/${payload.id}`, payload)
    } else {
      await request.post('/api/bills', payload)
    }
    ElMessage.success(payload.id ? '账单已更新' : '账单已新增')
    dialogVisible.value = false
    if (quickSave) {
      resetForm()
    }
    await loadBills()
  } finally {
    loading.saving = false
  }
}

async function removeBill(billId: number) {
  if (loading.deleting) return
  await ElMessageBox.confirm('删除后不可恢复，是否继续？', '删除账单', { type: 'warning' })
  loading.deleting = true
  try {
    await request.delete(`/api/bills/${billId}`)
    ElMessage.success('账单已删除')
    await loadBills()
  } finally {
    loading.deleting = false
  }
}

async function removeSelectedBills() {
  if (loading.deleting) return
  if (!selectedIds.value.length) {
    ElMessage.warning('请先勾选要删除的账单')
    return
  }
  await ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 条账单吗？`, '批量删除账单', {
    type: 'warning'
  })
  loading.deleting = true
  try {
    await request.delete('/api/bills/batch', { data: { ids: selectedIds.value } })
    ElMessage.success('选中账单已删除')
    await loadBills()
  } finally {
    loading.deleting = false
  }
}

watch(
  () => form.billType,
  () => {
    syncFormDefaults()
  }
)

watch(
  () => form.bookId,
  async (bookId, oldBookId) => {
    if (!dialogVisible.value || !bookId || bookId === oldBookId) return
    accounts.value = await loadAccountsForBook(bookId)
    if (!accounts.value.some((item) => item.id === form.accountId)) {
      form.accountId = accounts.value[0]?.id
    }
  }
)

watch(
  () => filters.billType,
  () => {
    if (filters.categoryId && !filterCategoryOptions.value.some((item) => item.id === filters.categoryId)) {
      filters.categoryId = undefined
    }
  }
)

watch(
  () => [
    filters.month,
    filters.billType,
    filters.sourceType,
    filters.accountId,
    filters.categoryId,
    filters.keyword,
    filters.dateRange?.[0],
    filters.dateRange?.[1]
  ],
  triggerFilterSearch
)

watch(currentBookId, async (value, oldValue) => {
  if (!value || value === oldValue) return
  pageState.pageNum = 1
  await loadReferenceData()
  await loadBills()
}, { immediate: false })

onMounted(async () => {
  await Promise.all([loadBooks(true), loadCurrencyDicts()])
  await loadReferenceData()
  resetForm()
  await loadBills()
})
</script>

<style scoped lang="stylus">
.bill-dialog-form {
  display: grid;
  gap: 1rem;
}

.bill-dialog-intro {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
  padding: 0.875rem 1rem;
  border-radius: 1rem;
  background: linear-gradient(135deg, #f7fcfb 0%, #edf7f5 100%);
  border: 0.0625rem solid rgba(83, 181, 171, 0.16);
}

.bill-dialog-intro strong,
.bill-dialog-card-head strong {
  color: #17252e;
  font-size: 0.9375rem;
  font-weight: 800;
}

.bill-dialog-intro span,
.bill-dialog-card-head small {
  color: #7b8c96;
  font-size: 0.75rem;
  line-height: 1.6;
  display: block;
  margin-top: 0.3125rem;
}

.bill-dialog-type-switch {
  min-width: 12.5rem;
}

.bill-dialog-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(20rem, 0.85fr);
  gap: 0.875rem;
  align-items: start;
}

.bill-dialog-card {
  padding: 1rem;
  border-radius: 1.125rem;
  background: #fbfdfd;
  border: 0.0625rem solid rgba(64, 141, 134, 0.1);
}

.bill-dialog-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.625rem;
  margin-bottom: 0.875rem;
  flex-wrap: wrap;
}

.bill-dialog-fields {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  gap: 0.875rem;
}

.ledger-amount-field {
  width: 100%;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 0.5rem;
}

.ledger-amount-field span {
  min-width: 4.25rem;
  padding: 0.5rem 0.625rem;
  border-radius: 0.625rem;
  background: #edf8f5;
  color: #0a645d;
  font-size: 0.75rem;
  font-weight: 800;
  text-align: center;
}

.bill-dialog-amount-field :deep(.el-input-number) {
  width: 100%;
  min-width: 9rem;
}

.bill-dialog-amount-field :deep(.el-input-number .el-input__wrapper) {
  padding-left: 2.5rem;
  padding-right: 2.5rem;
}

.bill-dialog-field-span,
.bill-dialog-category-item {
  margin-bottom: 0;
}

.bill-dialog-field-span,
.bill-dialog-date-field,
.bill-dialog-amount-field {
  grid-column: 1 / -1;
}

.bill-dialog-date-field :deep(.el-date-editor),
.bill-dialog-date-field :deep(.el-input__wrapper) {
  width: 100%;
}

.bill-dialog-date-field :deep(.el-input__inner) {
  font-variant-numeric: tabular-nums;
}

.bill-dialog-category-picker {
  display: grid;
  gap: 0.75rem;
  width: 100%;
}

.bill-dialog-category-block {
  width: 100%;
  border-radius: 1rem;
  background: linear-gradient(180deg, #f7fbfa 0%, #edf6f4 100%);
  padding: 0.875rem;
}

.bill-dialog-category-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.625rem;
}

.bill-dialog-category-grid > button,
.bill-dialog-subcategory-panel > button {
  width: 100%;
  min-width: 0;
  border: 0;
  border-radius: 0.875rem;
  background: transparent;
  min-height: 4.875rem;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 0.5rem;
  cursor: pointer;
  color: #1d313c;
  font-size: 0.8125rem;
  font-weight: 600;
  line-height: 1.2;
  transition: background 0.18s ease, color 0.18s ease, transform 0.18s ease;
}

.bill-dialog-category-grid > button:hover,
.bill-dialog-category-grid > button.active,
.bill-dialog-subcategory-panel > button:hover,
.bill-dialog-subcategory-panel > button.active {
  background: #ffffff;
  color: #169980;
  transform: translateY(-0.0625rem);
}

.bill-dialog-category-grid span,
.bill-dialog-subcategory-panel span {
  width: 2.5rem;
  height: 2.5rem;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #ffffff;
  color: inherit;
  box-shadow: 0 0.125rem 0.625rem rgba(29, 50, 61, 0.06);
}

.bill-dialog-category-grid .el-icon,
.bill-dialog-subcategory-panel .el-icon {
  font-size: 1.25rem;
}

.bill-dialog-subcategory-panel {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.625rem;
  padding: 0.75rem;
  align-content: start;
  border-radius: 1rem;
  background: rgba(255, 255, 255, 0.72);
}

.bill-dialog-category-grid.is-income {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

@media (max-width: 47.5rem) {
  .bill-dialog-intro,
  .bill-dialog-layout {
    grid-template-columns: 1fr;
    flex-direction: column;
    align-items: stretch;
  }

  .bill-dialog-fields,
  .bill-dialog-category-grid,
  .bill-dialog-subcategory-panel,
  .bill-dialog-category-grid.is-income {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .bill-dialog-fields {
    grid-template-columns: 1fr;
  }

  .bill-dialog-type-switch {
    width: 100%;
  }
}
</style>
