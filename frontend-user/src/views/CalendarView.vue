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

        <div class="calendar-legend-row">
          <span class="is-surplus">收入不低于支出</span>
          <span class="is-deficit">支出较高</span>
          <span class="is-empty">无记录</span>
        </div>

        <div v-loading="loading" class="calendar-constellation">
          <div class="calendar-month-watermark">{{ selectedMonthWatermark }}</div>
          <svg class="calendar-spiral-line" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
            <polyline :points="spiralLinePoints" />
          </svg>
          <button
            v-for="cell in constellationCells"
            :key="cell.date"
            type="button"
            class="calendar-star-day"
            :class="{
              today: cell.isToday,
              selected: selectedDate === cell.date,
              active: Boolean(cell.group?.items.length),
              'is-surplus': dayStatus(cell) === 'surplus',
              'is-deficit': dayStatus(cell) === 'deficit',
              'is-empty': dayStatus(cell) === 'empty'
            }"
            :style="{ left: cell.x, top: cell.y }"
            :aria-label="dayAriaLabel(cell)"
            @click="selectDate(cell.date)"
          >
            <span>{{ cell.day }}</span>
            <small v-if="cell.group?.items.length">{{ cell.group.items.length }}</small>
            <em class="calendar-day-tooltip">
              <b>{{ cell.day }}日</b>
              <strong>结余: {{ formatBalanceTooltip(cell) }}</strong>
            </em>
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
      width="min(26.25rem, calc(100vw - 1.75rem))"
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
const accountNameMap = computed(() =>
  Object.fromEntries(accounts.value.map((item) => [item.id, item.accountName]))
)

const categoryNameMap = computed(() =>
  Object.fromEntries(categories.value.map((item) => [item.id, item.categoryName]))
)

const monthSummary = computed(() => summarizeBills(monthlyBills.value))
const activeDayCount = computed(() => dayGroupMap.value.size)
const selectedMonthLabel = computed(() => `${selectedMonth.value.replace('-', '年')}月`)
const selectedMonthWatermark = computed(() => `${Number(selectedMonth.value.split('-')[1])}月`)
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

const constellationCells = computed(() => {
  const [year, month] = selectedMonth.value.split('-').map(Number)
  const totalDays = new Date(year, month, 0).getDate()
  const today = currentDate()
  return Array.from({ length: totalDays }, (_, index) => {
    const day = index + 1
    const date = `${selectedMonth.value}-${String(day).padStart(2, '0')}`
    const position = constellationPosition(index, totalDays)
    return {
      date,
      day,
      x: `${position.x}%`,
      y: `${position.y}%`,
      rawX: position.x,
      rawY: position.y,
      inMonth: true,
      isToday: date === today,
      lunarLabel: lunarDayLabel(date),
      group: dayGroupMap.value.get(date)
    }
  })
})

const spiralLinePoints = computed(() =>
  constellationCells.value.map((cell) => `${cell.rawX.toFixed(2)},${cell.rawY.toFixed(2)}`).join(' ')
)

function summarizeBills(bills: any[]) {
  const income = bills
    .filter((item) => item.billType === 'INCOME')
    .reduce((sum, item) => sum + Number(item.amount || 0), 0)
  const expense = bills
    .filter((item) => item.billType === 'EXPENSE')
    .reduce((sum, item) => sum + Number(item.amount || 0), 0)
  return { income, expense, balance: income - expense }
}

function constellationPosition(index: number, total: number) {
  const progress = total <= 1 ? 1 : index / (total - 1)
  const angle = -Math.PI * 0.65 + progress * Math.PI * 2 * 2.48
  const radiusX = 7 + progress * 43
  const radiusY = 6 + progress * 40
  const x = 50 + Math.cos(angle) * radiusX
  const y = 50 + Math.sin(angle) * radiusY
  return {
    x: Math.min(94, Math.max(6, x)),
    y: Math.min(91, Math.max(9, y))
  }
}

function dayStatus(cell: any) {
  if (!cell.group?.items.length) return 'empty'
  return Number(cell.group.income || 0) >= Number(cell.group.expense || 0) ? 'surplus' : 'deficit'
}

function dayAriaLabel(cell: any) {
  const group = cell.group
  if (!group?.items.length) return `${cell.day}日，无账单记录`
  const status = Number(group.income || 0) >= Number(group.expense || 0) ? '收入不低于支出' : '支出大于收入'
  return `${cell.day}日，${status}，共${group.items.length}笔账单`
}

function formatBalanceTooltip(cell: any) {
  return formatSignedCurrency(Number(cell.group?.balance || 0))
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

<style lang="stylus">
.calendar-shell {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  min-height: calc(100vh - 5.75rem);
}

.calendar-hero,
.calendar-grid-card,
.calendar-detail-card {
  background: #fff;
  border: 0.0625rem solid #e3e9ed;
  border-radius: 0.625rem;
  box-shadow: 0 0.625rem 1.375rem rgba(25, 38, 49, 0.045);
}

.calendar-hero {
  display: grid;
  grid-template-columns: minmax(16.25rem, 0.72fr) minmax(31.25rem, 1fr);
  gap: 1.25rem;
  align-items: center;
  padding: 1rem 1.125rem;
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
  gap: 0.75rem;
}

.calendar-grid-head {
  margin-top: 0;
  padding-bottom: 0.75rem;
  border-bottom: 0.0625rem solid #edf2f5;
}

.calendar-title-row h2,
.calendar-grid-head h3,
.calendar-detail-head h3 {
  margin: 0;
  color: #10242f;
}

.calendar-title-row h2 {
  font-size: 1.375rem;
}

.calendar-hero-left p,
.calendar-grid-head span,
.calendar-detail-head span,
.calendar-detail-summary span,
.calendar-bill-meta,
.calendar-bill-main p {
  color: #83909d;
  font-size: 0.8125rem;
}

.calendar-month-picker {
  width: 9.375rem;
  flex: 0 0 9.375rem;
}

.calendar-summary-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.75rem;
}

.calendar-stat {
  min-width: 0;
  padding: 0.75rem 0.75rem 0.75rem 0.875rem;
  background: #f8fafb;
  border-radius: 0.5rem;
}

.calendar-workbench {
  display: grid;
  width: 100%;
  grid-template-columns: minmax(38.75rem, 1fr) clamp(30rem, 27vw, 32.5rem);
  gap: 0.75rem;
  flex: 1;
  min-height: 0;
  align-items: stretch;
}

.calendar-grid-card,
.calendar-detail-card {
  height: calc(100vh - 13.625rem);
  min-height: 43.75rem;
  padding: 1.125rem 1.25rem;
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

.calendar-legend-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.5rem;
  margin: 0.875rem 0 0.625rem;
}

.calendar-legend-row span {
  position: relative;
  padding: 0.3125rem 0.625rem 0.3125rem 1.375rem;
  color: #6b7480;
  font-size: 0.75rem;
  font-weight: 700;
  border-radius: 62.4375rem;
  background: #f6f8fa;
}

.calendar-legend-row span::before {
  content: '';
  position: absolute;
  left: 0.625rem;
  top: 50%;
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 50%;
  transform: translateY(-50%);
}

.calendar-legend-row .is-surplus::before {
  background: #2ecf9b;
}

.calendar-legend-row .is-deficit::before {
  background: #ff6b80;
}

.calendar-legend-row .is-empty::before {
  background: #cfd6df;
}

.calendar-constellation {
  position: relative;
  width: 100%;
  flex: 1;
  min-height: 30rem;
  margin: 0.25rem 0 0;
  overflow: visible;
  border-radius: 0.75rem;
  background:
    radial-gradient(circle at 34% 48%, rgba(46, 207, 155, 0.06), transparent 24%),
    radial-gradient(circle at 70% 58%, rgba(255, 107, 128, 0.045), transparent 26%),
    #fff;
  border: 0;
  box-shadow: none;
}

.calendar-constellation::before {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 50% 50%, rgba(240, 244, 246, 0.64), transparent 58%);
  pointer-events: none;
}

.calendar-month-watermark {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: rgba(31, 42, 55, 0.05);
  font-size: 4rem;
  font-weight: 900;
  pointer-events: none;
  user-select: none;
}

.calendar-spiral-line {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
}

.calendar-spiral-line polyline {
  fill: none;
  stroke: rgba(174, 184, 194, 0.42);
  stroke-width: 0.38;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-dasharray: 1.2 1.5;
}

.calendar-star-day {
  position: absolute;
  z-index: 2;
  width: 1.75rem;
  height: 1.75rem;
  border: 0.0625rem solid rgba(255, 255, 255, 0.8);
  border-radius: 50%;
  display: grid;
  place-items: center;
  padding: 0;
  color: #fff;
  font: inherit;
  font-size: 0.75rem;
  font-weight: 800;
  cursor: pointer;
  transform: translate(-50%, -50%);
  transition: transform 0.16s ease, box-shadow 0.16s ease, background 0.16s ease;
}

.calendar-star-day span {
  line-height: 1;
  position: relative;
  z-index: 1;
}

.calendar-star-day small {
  position: absolute;
  right: -0.1875rem;
  top: -0.25rem;
  min-width: 0.875rem;
  height: 0.875rem;
  padding: 0 0.1875rem;
  border-radius: 62.4375rem;
  background: #fff;
  color: #4e5b55;
  font-size: 0.5625rem;
  line-height: 0.875rem;
  box-shadow: 0 0.1875rem 0.5rem rgba(76, 61, 42, 0.14);
}

.calendar-day-tooltip {
  position: absolute;
  left: 50%;
  bottom: calc(100% + 0.625rem);
  min-width: 4.375rem;
  padding: 0.375rem 0.5rem;
  border-radius: 0.25rem;
  background: #172132;
  color: #fff;
  box-shadow: 0 0.5rem 1rem rgba(23, 33, 50, 0.18);
  opacity: 0;
  pointer-events: none;
  transform: translate(-50%, 0.25rem);
  transition: opacity 0.16s ease, transform 0.16s ease;
  white-space: nowrap;
  z-index: 5;
}

.calendar-day-tooltip::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 100%;
  border: 0.3125rem solid transparent;
  border-top-color: #172132;
  transform: translateX(-50%);
}

.calendar-day-tooltip b,
.calendar-day-tooltip strong {
  display: block;
  color: #fff;
  font-style: normal;
  line-height: 1.25;
  text-align: left;
}

.calendar-day-tooltip b {
  font-size: 0.75rem;
}

.calendar-day-tooltip strong {
  margin-top: 0.0625rem;
  font-size: 0.6875rem;
  font-weight: 800;
}

.calendar-star-day:hover .calendar-day-tooltip,
.calendar-star-day:focus-visible .calendar-day-tooltip {
  opacity: 1;
  transform: translate(-50%, 0);
}

.calendar-star-day.is-surplus {
  background: #2ecf9b;
  box-shadow: 0 0.375rem 0.875rem rgba(46, 207, 155, 0.24);
}

.calendar-star-day.is-deficit {
  background: #ff6b80;
  box-shadow: 0 0.375rem 0.875rem rgba(255, 107, 128, 0.24);
}

.calendar-star-day.is-empty {
  background: #cfd6df;
  color: #fff;
  box-shadow: 0 0.3125rem 0.75rem rgba(114, 126, 139, 0.18);
}

.calendar-star-day:hover {
  transform: translate(-50%, -50%) scale(1.14);
  box-shadow: 0 0.5rem 1rem rgba(31, 42, 55, 0.14), 0 0 0 0.1875rem rgba(255, 255, 255, 0.86);
}

.calendar-star-day.selected {
  transform: translate(-50%, -50%) scale(1.2);
  box-shadow: 0 0.625rem 1.125rem rgba(31, 42, 55, 0.16), 0 0 0 0.1875rem rgba(255, 255, 255, 0.95), 0 0 0 0.375rem rgba(46, 207, 155, 0.24);
}

.calendar-star-day.today:not(.selected)::after {
  content: '';
  position: absolute;
  inset: -0.3125rem;
  border: 0.0625rem solid rgba(46, 207, 155, 0.58);
  border-radius: 50%;
}

.calendar-month-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.calendar-month-actions .el-button {
  width: 1.75rem;
  height: 1.75rem;
}

.calendar-detail-card {
  display: flex;
  flex-direction: column;
  max-height: none;
}

.calendar-detail-summary {
  margin: 0.875rem 0 0.625rem;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.5rem;
}

.calendar-detail-summary > div {
  padding: 0.625rem 0.625rem 0.625rem 0.75rem;
  border-radius: 0.5rem;
  background: #f8fafb;
}

.calendar-detail-summary .ledger-summary-item strong {
  font-size: 1rem;
}

.calendar-bill-list {
  overflow-y: auto;
  min-height: 0;
  flex: 1;
  padding-right: 0.125rem;
  scrollbar-width: none;
}

.calendar-empty-day {
  flex: 1;
  display: grid;
  place-items: center;
  min-height: 11.25rem;
}

.calendar-bill-list::-webkit-scrollbar {
  display: none;
}

.calendar-bill-item {
  width: 100%;
  border: 0;
  border-bottom: 0.0625rem solid #edf2f5;
  background: transparent;
  color: inherit;
  display: flex;
  gap: 0.75rem;
  padding: 0.75rem 0;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.16s ease, padding 0.16s ease;
}

.calendar-bill-item:hover {
  margin: 0 -0.5rem;
  padding: 0.75rem 0.5rem;
  border-radius: 0.5rem;
  background: #f5fbf9;
}

.calendar-bill-item:last-child {
  border-bottom: 0;
}

.calendar-bill-icon {
  width: 2.25rem;
  height: 2.25rem;
  border-radius: 0.625rem;
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
  gap: 0.625rem;
}

.calendar-bill-title strong,
.calendar-bill-title span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.calendar-bill-title strong {
  color: #10242f;
  font-size: 0.9375rem;
}

.calendar-bill-title span {
  flex: 0 0 auto;
  font-weight: 800;
}

.calendar-bill-meta {
  margin-top: 0.3125rem;
  flex-wrap: wrap;
  justify-content: flex-start;
}

.calendar-bill-main p {
  margin: 0.4375rem 0 0;
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

@media (max-width: 73.75rem) {
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

@media (max-width: 47.5rem) {
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

  .calendar-constellation {
    min-height: 24rem;
  }

  .calendar-star-day {
    width: 1.625rem;
    height: 1.625rem;
    font-size: 0.75rem;
  }
}
</style>
