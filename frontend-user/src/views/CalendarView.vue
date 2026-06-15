<template>
  <div class="calendar-shell">
    <section class="calendar-hero">
      <div class="calendar-hero-left">
        <div class="calendar-title-row">
          <h2>日历视图</h2>
        </div>
        <p>按月查看每日收支，点击日期查看当天账单流水。</p>
      </div>

      <div class="calendar-summary-stats">
        <div class="calendar-stat ledger-summary-item expense">
          <span>支出</span>
          <strong>{{ formatCurrency(monthSummary.expense) }}</strong>
        </div>
        <div class="calendar-stat ledger-summary-item income">
          <span>收入</span>
          <strong>{{ formatCurrency(monthSummary.income) }}</strong>
        </div>
        <div class="calendar-stat ledger-summary-item balance">
          <span>结余</span>
          <strong>{{ formatSignedCurrency(monthSummary.balance) }}</strong>
        </div>
      </div>
    </section>

    <section class="calendar-workbench">
      <main class="calendar-grid-card">
        <div class="calendar-grid-head">
          <div>
            <h3>{{ selectedMonthLabel }}</h3>
            <span>共 {{ monthlyBills.length }} 条记录，{{ activeDayCount }} 天有流水</span>
          </div>
          <div class="calendar-month-actions">
            <el-button :icon="CaretTop" text @click="shiftMonth(-1)" />
            <el-date-picker
              v-model="selectedMonth"
              type="month"
              value-format="YYYY-MM"
              :clearable="false"
              class="calendar-month-picker"
            />
            <el-button :icon="CaretBottom" text @click="shiftMonth(1)" />
          </div>
        </div>

        <div class="calendar-week-row">
          <span v-for="item in weekLabels" :key="item">{{ item }}</span>
        </div>

        <div v-loading="loading" class="calendar-grid">
          <button
            v-for="cell in calendarCells"
            :key="cell.date"
            type="button"
            class="calendar-cell"
            :class="{
              muted: !cell.inMonth,
              today: cell.isToday,
              selected: selectedDate === cell.date,
              active: Boolean(cell.group?.items.length)
            }"
            @click="selectDate(cell.date)"
          >
            <div class="calendar-date-badge">
              <span>{{ cell.day }}</span>
              <small>{{ cell.lunarLabel }}</small>
            </div>
            <em v-if="cell.group?.items.length">{{ cell.group.items.length }}笔</em>
          </button>
        </div>
      </main>

      <aside class="calendar-detail-card">
        <div class="calendar-detail-head">
          <div>
            <span>选中日期</span>
            <h3>{{ selectedDateTitle }}</h3>
          </div>
          <el-tag v-if="selectedDayGroup.items.length" type="success" effect="plain">
            {{ selectedDayGroup.items.length }} 条
          </el-tag>
        </div>

        <div class="calendar-detail-summary">
          <div class="ledger-summary-item income">
            <span>收入</span>
            <strong>{{ formatCurrency(selectedDayGroup.income) }}</strong>
          </div>
          <div class="ledger-summary-item expense">
            <span>支出</span>
            <strong>{{ formatCurrency(selectedDayGroup.expense) }}</strong>
          </div>
          <div class="ledger-summary-item balance">
            <span>结余</span>
            <strong>{{ formatSignedCurrency(selectedDayGroup.balance) }}</strong>
          </div>
        </div>

        <div v-if="!selectedDayGroup.items.length" class="calendar-empty-day">
          <el-empty description="当天没有账单" />
        </div>

        <div v-else class="calendar-bill-list">
          <button
            v-for="bill in selectedDayGroup.items"
            :key="bill.id"
            type="button"
            class="calendar-bill-item"
            @click="openBillDetail(bill)"
          >
            <div class="calendar-bill-icon" :class="bill.billType === 'EXPENSE' ? 'is-expense' : 'is-income'">
              {{ bill.billType === 'EXPENSE' ? '支' : '收' }}
            </div>
            <div class="calendar-bill-main">
              <div class="calendar-bill-title">
                <strong>{{ categoryNameMap[bill.categoryId] || bill.merchantName || '未分类' }}</strong>
                <span :class="bill.billType === 'EXPENSE' ? 'is-negative' : 'is-positive'">
                  {{ bill.billType === 'EXPENSE' ? '-' : '+' }}{{ formatCurrency(bill.amount) }}
                </span>
              </div>
              <div class="calendar-bill-meta">
                <span>{{ formatBillTime(bill) }}</span>
                <span>{{ accountNameMap[bill.accountId] || '未知账户' }}</span>
                <span>{{ sourceTypeLabel(bill.sourceType) }}</span>
              </div>
              <p>{{ bill.remark || bill.merchantName || '暂无备注' }}</p>
            </div>
          </button>
        </div>
      </aside>
    </section>

    <el-dialog
      v-model="billDialogVisible"
      class="stats-bill-dialog"
      width="min(420px, calc(100vw - 28px))"
      :show-close="false"
      append-to-body
    >
      <template #header>
        <div class="stats-bill-dialog-head">
          <button type="button" class="stats-bill-close" @click="billDialogVisible = false">×</button>
          <strong>账单详情</strong>
        </div>
      </template>
      <div v-if="selectedBill" class="stats-bill-detail">
        <div class="stats-bill-hero" :class="selectedBill.billType === 'EXPENSE' ? 'expense' : 'income'">
          <div class="stats-bill-icon">
            {{ selectedBill.billType === 'EXPENSE' ? '支' : '收' }}
          </div>
          <div>
            <strong>{{ categoryNameMap[selectedBill.categoryId] || selectedBill.merchantName || '未分类' }}</strong>
            <span>{{ formatBillTime(selectedBill) }}</span>
          </div>
          <em>{{ selectedBill.billType === 'EXPENSE' ? '-' : '+' }}{{ formatCurrency(selectedBill.amount) }}</em>
        </div>

        <div class="stats-bill-section">
          <div><span>账单分类</span><strong>{{ categoryNameMap[selectedBill.categoryId] || '未分类' }}</strong></div>
          <div><span>归属账本</span><strong>{{ currentBookName }}</strong></div>
          <div><span>收支账户</span><strong>{{ accountNameMap[selectedBill.accountId] || '未知账户' }}</strong></div>
          <div><span>商户来源</span><strong>{{ selectedBill.merchantName || '未填写' }}</strong></div>
          <div><span>备注</span><strong>{{ selectedBill.remark || '暂无备注' }}</strong></div>
          <div><span>记录方式</span><strong>{{ sourceTypeLabel(selectedBill.sourceType) }}</strong></div>
        </div>

        <div class="stats-bill-section">
          <div><span>账单时间</span><strong>{{ formatBillTime(selectedBill) }}</strong></div>
          <div><span>账单类型</span><strong>{{ selectedBill.billType === 'EXPENSE' ? '支出' : '收入' }}</strong></div>
          <div><span>账单编号</span><strong>#{{ selectedBill.id }}</strong></div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { CaretBottom, CaretTop } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useBookContext } from '@/composables/useBookContext'
import request from '@/utils/request'

const { books, currentBookId, loadBooks } = useBookContext()
const selectedMonth = ref(currentMonth())
const selectedDate = ref(currentDate())
const monthlyBills = ref<any[]>([])
const accounts = ref<any[]>([])
const categories = ref<any[]>([])
const loading = ref(false)
const billDialogVisible = ref(false)
const selectedBill = ref<any>()
const weekLabels = ['一', '二', '三', '四', '五', '六', '日']

const accountNameMap = computed(() =>
  Object.fromEntries(accounts.value.map((item) => [item.id, item.accountName]))
)

const categoryNameMap = computed(() =>
  Object.fromEntries(categories.value.map((item) => [item.id, item.categoryName]))
)

const monthSummary = computed(() => summarizeBills(monthlyBills.value))
const activeDayCount = computed(() => dayGroupMap.value.size)
const selectedMonthLabel = computed(() => `${selectedMonth.value.replace('-', '年')}月`)
const selectedDateTitle = computed(() => `${formatDayTitle(selectedDate.value)} ${weekdayName(selectedDate.value)}`)
const currentBookName = computed(() => books.value.find((book) => book.id === currentBookId.value)?.bookName || '当前账本')

const dayGroupMap = computed(() => {
  const groups = new Map<string, any[]>()
  monthlyBills.value.forEach((bill) => {
    if (!bill.billDate) return
    const items = groups.get(bill.billDate) || []
    items.push(bill)
    groups.set(bill.billDate, items)
  })
  return new Map(
    Array.from(groups.entries()).map(([date, items]) => [
      date,
      {
        date,
        items: sortBills(items),
        ...summarizeBills(items)
      }
    ])
  )
})

const selectedDayGroup = computed(() =>
  dayGroupMap.value.get(selectedDate.value) || {
    date: selectedDate.value,
    items: [],
    income: 0,
    expense: 0,
    balance: 0
  }
)

const calendarCells = computed(() => {
  const [year, month] = selectedMonth.value.split('-').map(Number)
  const firstDay = new Date(year, month - 1, 1)
  const offset = (firstDay.getDay() + 6) % 7
  const start = new Date(year, month - 1, 1 - offset)
  const today = currentDate()

  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(start)
    date.setDate(start.getDate() + index)
    const value = formatLocalDate(date)
    return {
      date: value,
      day: date.getDate(),
      inMonth: value.startsWith(selectedMonth.value),
      isToday: value === today,
      lunarLabel: lunarDayLabel(value),
      group: dayGroupMap.value.get(value)
    }
  })
})

function summarizeBills(bills: any[]) {
  const income = bills
    .filter((item) => item.billType === 'INCOME')
    .reduce((sum, item) => sum + Number(item.amount || 0), 0)
  const expense = bills
    .filter((item) => item.billType === 'EXPENSE')
    .reduce((sum, item) => sum + Number(item.amount || 0), 0)
  return { income, expense, balance: income - expense }
}

function sortBills(items: any[]) {
  return [...items].sort((a, b) => {
    const left = String(a.billTime || a.createdTime || a.billDate || '')
    const right = String(b.billTime || b.createdTime || b.billDate || '')
    return right.localeCompare(left)
  })
}

function currentMonth() {
  return currentDate().slice(0, 7)
}

function currentDate() {
  return formatLocalDate(new Date())
}

function formatLocalDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function formatCurrency(value: number | string) {
  return `¥${Number(value || 0).toFixed(2)}`
}

function formatSignedCurrency(value: number) {
  const amount = formatCurrency(Math.abs(value))
  if (value > 0) return `+${amount}`
  if (value < 0) return `-${amount}`
  return amount
}

function formatDayTitle(date: string) {
  const [, month, day] = date.split('-')
  return `${Number(month)}月${Number(day)}日`
}

function weekdayName(date: string) {
  return `周${'日一二三四五六'[new Date(`${date}T00:00:00`).getDay()]}`
}

function lunarDayLabel(date: string) {
  const lunar = new Intl.DateTimeFormat('zh-CN-u-ca-chinese', { month: 'long', day: 'numeric' })
    .format(new Date(`${date}T00:00:00`))
  const matched = lunar.match(/(\d+)日/)
  if (!matched) return lunar.replace(/^\S+月/, '')
  return lunarDayName(Number(matched[1]))
}

function lunarDayName(day: number) {
  const names = [
    '初一', '初二', '初三', '初四', '初五', '初六', '初七', '初八', '初九', '初十',
    '十一', '十二', '十三', '十四', '十五', '十六', '十七', '十八', '十九', '二十',
    '廿一', '廿二', '廿三', '廿四', '廿五', '廿六', '廿七', '廿八', '廿九', '三十'
  ]
  return names[day - 1] || ''
}

function formatBillTime(row: any) {
  const value = String(row?.billTime || row?.createdTime || row?.billDate || '')
  if (!value) return '-'
  const normalized = value.replace('T', ' ')
  return normalized.length >= 16 ? normalized.slice(0, 16).replace(/-/g, '/') : normalized.replace(/-/g, '/')
}

function sourceTypeLabel(value?: string) {
  const normalized = String(value || '').trim().toUpperCase()
  if (normalized === 'AUTO' || value === '自动记账') return '自动记账'
  if (normalized === 'OCR' || value === 'OCR识别' || value === 'OCR导入') return 'OCR录入'
  if (normalized === 'IMPORT' || normalized === 'EXCEL' || value === 'Excel导入') return 'Excel导入'
  return '手动录入'
}

function selectDate(date: string) {
  selectedDate.value = date
}

function shiftMonth(delta: number) {
  const [year, month] = selectedMonth.value.split('-').map(Number)
  const next = new Date(year, month - 1 + delta, 1)
  selectedMonth.value = `${next.getFullYear()}-${String(next.getMonth() + 1).padStart(2, '0')}`
}

function openBillDetail(bill: any) {
  selectedBill.value = bill
  billDialogVisible.value = true
}

function resetSelectedDate() {
  const today = currentDate()
  if (today.startsWith(selectedMonth.value)) {
    selectedDate.value = today
    return
  }
  const firstBillDate = Array.from(dayGroupMap.value.keys()).sort()[0]
  selectedDate.value = firstBillDate || `${selectedMonth.value}-01`
}

async function loadReferenceData() {
  if (!currentBookId.value) return
  const [accountList, expenseCategories, incomeCategories] = await Promise.all([
    request.get('/api/accounts', { params: { bookId: currentBookId.value } }),
    request.get('/api/categories', { params: { bookId: currentBookId.value, categoryType: 'EXPENSE' } }),
    request.get('/api/categories', { params: { bookId: currentBookId.value, categoryType: 'INCOME' } })
  ])
  accounts.value = accountList
  categories.value = [...expenseCategories, ...incomeCategories]
}

async function loadMonthlyBills() {
  if (!currentBookId.value || loading.value) return
  loading.value = true
  try {
    const pageSize = 500
    const records: any[] = []
    let pageNum = 1
    let total = 0
    do {
      const result = await request.get('/api/bills', {
        params: {
          pageNum,
          pageSize,
          bookId: currentBookId.value,
          month: selectedMonth.value
        }
      })
      records.push(...(result.records || []))
      total = Number(result.total || records.length)
      pageNum += 1
    } while (records.length < total)
    monthlyBills.value = records
    resetSelectedDate()
  } catch {
    ElMessage.error('日历账单加载失败')
  } finally {
    loading.value = false
  }
}

watch(selectedMonth, loadMonthlyBills)

watch(currentBookId, async (value, oldValue) => {
  if (!value || value === oldValue) return
  await loadReferenceData()
  await loadMonthlyBills()
})

onMounted(async () => {
  await loadBooks()
  await loadReferenceData()
  await loadMonthlyBills()
})
</script>

<style>
.calendar-shell {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: calc(100vh - 92px);
}

.calendar-hero,
.calendar-grid-card,
.calendar-detail-card {
  background: #fff;
  border: 1px solid #e3e9ed;
  border-radius: 10px;
  box-shadow: 0 10px 22px rgba(25, 38, 49, 0.045);
}

.calendar-hero {
  display: grid;
  grid-template-columns: minmax(260px, 0.72fr) minmax(500px, 1fr);
  gap: 20px;
  align-items: center;
  padding: 16px 18px;
}

.calendar-title-row,
.calendar-grid-head,
.calendar-detail-head,
.calendar-bill-title,
.calendar-bill-meta {
  display: flex;
  align-items: center;
}

.calendar-title-row,
.calendar-grid-head,
.calendar-detail-head {
  justify-content: space-between;
  gap: 12px;
}

.calendar-grid-head {
  margin-top: 0;
  padding-bottom: 12px;
  border-bottom: 1px solid #edf2f5;
}

.calendar-title-row h2,
.calendar-grid-head h3,
.calendar-detail-head h3 {
  margin: 0;
  color: #10242f;
}

.calendar-title-row h2 {
  font-size: 22px;
}

.calendar-hero-left p,
.calendar-grid-head span,
.calendar-detail-head span,
.calendar-detail-summary span,
.calendar-bill-meta,
.calendar-bill-main p {
  color: #83909d;
  font-size: 13px;
}

.calendar-month-picker {
  width: 150px;
  flex: 0 0 150px;
}

.calendar-summary-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.calendar-stat {
  min-width: 0;
  padding: 12px 12px 12px 14px;
  background: #f8fafb;
  border-radius: 8px;
}

.calendar-workbench {
  display: grid;
  width: 100%;
  grid-template-columns: minmax(620px, 1fr) clamp(480px, 27vw, 520px);
  gap: 12px;
  flex: 1;
  min-height: 0;
  align-items: stretch;
}

.calendar-grid-card,
.calendar-detail-card {
  height: calc(100vh - 218px);
  min-height: 700px;
  padding: 18px 20px;
  min-width: 0;
}

.calendar-grid-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  scrollbar-width: none;
}

.calendar-grid-card::-webkit-scrollbar {
  display: none;
}

.calendar-week-row,
.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  min-width: 0;
}

.calendar-week-row {
  margin-top: 14px;
  color: #71808d;
  font-weight: 800;
  text-align: center;
}

.calendar-week-row span {
  padding: 7px 0 8px;
  font-size: 13px;
}

.calendar-grid {
  flex: 1;
  min-height: 0;
  overflow: visible;
  border: 0;
  border-radius: 0;
  background: transparent;
  grid-template-rows: repeat(6, minmax(72px, 1fr));
  gap: 8px 4px;
  align-items: stretch;
}

.calendar-cell {
  position: relative;
  height: auto;
  min-height: 72px;
  border: 0;
  background: transparent;
  padding: 0;
  text-align: center;
  cursor: pointer;
  transition: background 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.calendar-cell:hover,
.calendar-cell.active {
  background: transparent;
}

.calendar-cell:hover .calendar-date-badge {
  background: #f3f8f8;
}

.calendar-cell.selected .calendar-date-badge {
  background: #1f8ee9;
  color: #fff;
  box-shadow: 0 8px 16px rgba(31, 142, 233, 0.24);
}

.calendar-cell.selected .calendar-date-badge span,
.calendar-cell.selected .calendar-date-badge small {
  color: #fff;
}

.calendar-cell.today:not(.selected) .calendar-date-badge {
  box-shadow: inset 0 0 0 1px #26a69a;
}

.calendar-cell.muted {
  background: transparent;
}

.calendar-cell.muted .calendar-date-badge {
  opacity: 0.45;
}

.calendar-date-badge {
  width: 44px;
  height: 44px;
  margin: 8px auto 0;
  border-radius: 50%;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 1px;
  transition: background 0.16s ease, box-shadow 0.16s ease;
}

.calendar-date-badge span {
  color: #152733;
  font-size: 15px;
  font-weight: 500;
  line-height: 1;
}

.calendar-date-badge small {
  color: #7c8792;
  font-size: 11px;
  line-height: 1;
}

.calendar-cell > em {
  position: absolute;
  left: calc(50% + 25px);
  top: 1px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: #eef4f5;
  color: #6d7d89;
  font-size: 11px;
  line-height: 18px;
  font-style: normal;
  font-weight: 700;
  white-space: nowrap;
}

.calendar-cell.selected > em {
  background: #e7f2ff;
  color: #1f7fcf;
}

.calendar-month-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.calendar-month-actions .el-button {
  width: 28px;
  height: 28px;
}

.calendar-detail-card {
  display: flex;
  flex-direction: column;
  max-height: none;
}

.calendar-detail-summary {
  margin: 14px 0 10px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.calendar-detail-summary > div {
  padding: 10px 10px 10px 12px;
  border-radius: 8px;
  background: #f8fafb;
}

.calendar-detail-summary .ledger-summary-item strong {
  font-size: 16px;
}

.calendar-bill-list {
  overflow-y: auto;
  min-height: 0;
  flex: 1;
  padding-right: 2px;
  scrollbar-width: none;
}

.calendar-empty-day {
  flex: 1;
  display: grid;
  place-items: center;
  min-height: 180px;
}

.calendar-bill-list::-webkit-scrollbar {
  display: none;
}

.calendar-bill-item {
  width: 100%;
  border: 0;
  border-bottom: 1px solid #edf2f5;
  background: transparent;
  color: inherit;
  display: flex;
  gap: 12px;
  padding: 12px 0;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.16s ease, padding 0.16s ease;
}

.calendar-bill-item:hover {
  margin: 0 -8px;
  padding: 12px 8px;
  border-radius: 8px;
  background: #f5fbf9;
}

.calendar-bill-item:last-child {
  border-bottom: 0;
}

.calendar-bill-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  font-weight: 800;
  flex: 0 0 auto;
}

.calendar-bill-icon.is-expense {
  background: #fff0f0;
  color: #ff4d4f;
}

.calendar-bill-icon.is-income {
  background: #eefaf6;
  color: #199673;
}

.calendar-bill-main {
  min-width: 0;
  flex: 1;
}

.calendar-bill-title,
.calendar-bill-meta {
  justify-content: space-between;
  gap: 10px;
}

.calendar-bill-title strong,
.calendar-bill-title span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.calendar-bill-title strong {
  color: #10242f;
  font-size: 15px;
}

.calendar-bill-title span {
  flex: 0 0 auto;
  font-weight: 800;
}

.calendar-bill-meta {
  margin-top: 5px;
  flex-wrap: wrap;
  justify-content: flex-start;
}

.calendar-bill-main p {
  margin: 7px 0 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.is-positive {
  color: #199673 !important;
}

.is-negative {
  color: #ff4d4f !important;
}

@media (max-width: 1180px) {
  .calendar-hero,
  .calendar-workbench {
    grid-template-columns: 1fr;
  }

  .calendar-grid-card,
  .calendar-detail-card {
    height: auto;
    min-height: 0;
  }
}

@media (max-width: 760px) {
  .calendar-title-row,
  .calendar-summary-stats,
  .calendar-detail-summary {
    grid-template-columns: 1fr;
  }

  .calendar-title-row,
  .calendar-grid-head,
  .calendar-detail-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .calendar-grid {
    grid-template-columns: repeat(7, minmax(42px, 1fr));
  }

  .calendar-week-row {
    grid-template-columns: repeat(7, minmax(42px, 1fr));
  }
}
</style>
