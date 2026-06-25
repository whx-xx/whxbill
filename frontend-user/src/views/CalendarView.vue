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
            <el-button :icon="CaretLeft" text @click="shiftMonth(-1)" />
            <el-date-picker
              v-model="selectedMonth"
              type="month"
              value-format="YYYY-MM"
              :clearable="false"
              class="calendar-month-picker"
            />
            <el-button :icon="CaretRight" text @click="shiftMonth(1)" />
          </div>
        </div>

        <div class="calendar-week-row" aria-hidden="true">
          <span v-for="weekday in weekLabels" :key="weekday">{{ weekday }}</span>
        </div>

        <div v-loading="loading" class="calendar-month-grid">
          <button
            v-for="cell in calendarCells"
            :key="cell.date"
            type="button"
            class="calendar-day-cell"
            :class="{
              today: cell.isToday,
              selected: selectedDate === cell.date,
              active: Boolean(cell.group?.items.length),
              muted: !cell.inMonth,
              'is-surplus': dayStatus(cell) === 'surplus',
              'is-deficit': dayStatus(cell) === 'deficit',
              'is-empty': dayStatus(cell) === 'empty'
            }"
            :aria-label="dayAriaLabel(cell)"
            @click="selectDate(cell.date)"
          >
            <span v-if="cell.isToday" class="calendar-today-mark">今</span>
            <strong>{{ cell.day }}</strong>
            <small>{{ dateTagLabel(cell.date, cell.lunarLabel) }}</small>
            <div v-if="cell.group?.items.length" class="calendar-day-money">
              <span v-if="Number(cell.group.expense || 0)" class="is-negative">-{{ compactAmount(cell.group.expense) }}</span>
              <span v-if="Number(cell.group.income || 0)" class="is-positive">+{{ compactAmount(cell.group.income) }}</span>
            </div>
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
import { CaretLeft, CaretRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useBookContext } from '@/composables/useBookContext'
import request from '@/utils/request'

const { books, currentBookId, loadBooks } = useBookContext()
const weekLabels = ['日', '一', '二', '三', '四', '五', '六']
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
  const offset = firstDay.getDay()
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

function compactAmount(value: number | string) {
  const numberValue = Number(value || 0)
  if (Math.abs(numberValue) >= 10000) return `${(numberValue / 10000).toFixed(1)}万`
  return numberValue.toFixed(numberValue >= 1000 ? 0 : 2).replace(/\.00$/, '')
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

function dateTagLabel(date: string, lunarLabel: string) {
  return solarFestivalLabel(date) || solarTermLabel(date) || lunarFestivalLabel(date, lunarLabel) || lunarLabel
}

function solarFestivalLabel(date: string) {
  const [, month, day] = date.split('-').map(Number)
  const labels: Record<string, string> = {
    '1-1': '元旦',
    '2-14': '情人节',
    '3-8': '妇女节',
    '5-1': '劳动节',
    '5-4': '青年节',
    '6-1': '儿童节',
    '10-1': '国庆节',
    '12-25': '圣诞节'
  }
  return labels[`${month}-${day}`] || ''
}

function lunarFestivalLabel(date: string, lunarLabel: string) {
  const lunarMonth = new Intl.DateTimeFormat('zh-CN-u-ca-chinese', { month: 'long' })
    .format(new Date(`${date}T00:00:00`))
  const monthName = lunarMonth.replace(/月.*/, '月')
  if (monthName.includes('正月') && lunarLabel === '初一') return '春节'
  if (monthName.includes('正月') && lunarLabel === '十五') return '元宵'
  if (monthName.includes('五月') && lunarLabel === '初五') return '端午'
  if (monthName.includes('七月') && lunarLabel === '初七') return '七夕'
  if (monthName.includes('八月') && lunarLabel === '十五') return '中秋'
  if (monthName.includes('九月') && lunarLabel === '初九') return '重阳'
  return ''
}

function solarTermLabel(date: string) {
  const [, month, day] = date.split('-').map(Number)
  const labels: Record<string, string> = {
    '1-5': '小寒', '1-20': '大寒',
    '2-4': '立春', '2-19': '雨水',
    '3-5': '惊蛰', '3-20': '春分',
    '4-4': '清明', '4-20': '谷雨',
    '5-5': '立夏', '5-21': '小满',
    '6-5': '芒种', '6-21': '夏至',
    '7-7': '小暑', '7-22': '大暑',
    '8-7': '立秋', '8-23': '处暑',
    '9-7': '白露', '9-23': '秋分',
    '10-8': '寒露', '10-23': '霜降',
    '11-7': '立冬', '11-22': '小雪',
    '12-7': '大雪', '12-21': '冬至'
  }
  return labels[`${month}-${day}`] || ''
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

.calendar-week-row,
.calendar-month-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
}

.calendar-week-row {
  margin: 0.875rem 0 0.5rem;
  color: #65717d;
  font-size: 0.8125rem;
  font-weight: 800;
  text-align: center;
}

.calendar-week-row span {
  padding: 0.25rem 0;
}

.calendar-month-grid {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  border: 0.0625rem solid #edf2f5;
  border-radius: 0.625rem;
  background: #edf2f5;
  gap: 0.0625rem;
}

.calendar-day-cell {
  position: relative;
  min-width: 0;
  min-height: 5.625rem;
  border: 0;
  background: #fff;
  color: #10242f;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.125rem;
  padding: 0.625rem 0.625rem 0.5rem;
  text-align: left;
  font: inherit;
  cursor: pointer;
  overflow: hidden;
  transition: background-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.calendar-day-cell strong {
  position: relative;
  z-index: 1;
  font-size: 1.125rem;
  line-height: 1.25;
}

.calendar-day-cell small {
  position: relative;
  z-index: 1;
  color: #8a96a3;
  font-size: 0.75rem;
  line-height: 1.2;
}

.calendar-day-cell.muted {
  background: #fafbfc;
  color: #b5bec7;
}

.calendar-day-cell.muted small,
.calendar-day-cell.muted .calendar-day-money span {
  color: #b5bec7 !important;
}

.calendar-day-cell:hover {
  z-index: 2;
  background: #f7fbfa;
  box-shadow: inset 0 0 0 0.0625rem rgba(20, 184, 144, 0.32);
}

.calendar-day-cell.selected {
  z-index: 3;
  background: #10b99c;
  color: #fff;
  box-shadow: inset 0 0 0 0.0625rem #10b99c, 0 0.5rem 1rem rgba(16, 185, 156, 0.22);
}

.calendar-day-cell.selected small,
.calendar-day-cell.selected .calendar-day-money span {
  color: #fff !important;
}

.calendar-day-cell.today:not(.selected) {
  background: #eefbf8;
}

.calendar-today-mark {
  position: absolute;
  right: 0.5rem;
  top: 0.4375rem;
  color: #0ba787;
  font-size: 1rem;
  font-weight: 900;
}

.calendar-day-money {
  width: 100%;
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: 0.0625rem;
  font-size: 0.75rem;
  font-weight: 800;
  line-height: 1.15;
}

.calendar-day-money span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

  .calendar-month-grid {
    min-height: 37.5rem;
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

  .calendar-month-grid {
    min-height: 31.25rem;
  }

  .calendar-day-cell {
    min-height: 4.875rem;
    padding: 0.5rem 0.4375rem 0.375rem;
  }

  .calendar-day-cell strong {
    font-size: 1rem;
  }

  .calendar-day-cell small,
  .calendar-day-money {
    font-size: 0.6875rem;
  }
}
</style>
