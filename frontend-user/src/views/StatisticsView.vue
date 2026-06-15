<template>
  <div class="stats-shell" :class="{ 'stats-shell-custom': mode === 'custom' }">
    <aside class="stats-side ledger-panel">
      <el-segmented v-model="mode" :options="modeOptions" block @change="handleModeChange" />
      <div class="stats-filter-block">
        <el-select v-model="selectedBookId" clearable placeholder="全部账本" @change="loadDashboard">
          <el-option v-for="book in books" :key="book.id" :label="book.bookName" :value="book.id" />
        </el-select>
        <el-date-picker
          v-if="mode === 'month'"
          v-model="selectedMonth"
          type="month"
          value-format="YYYY-MM"
          style="width:100%"
          @change="loadDashboard"
        />
        <el-date-picker
          v-else-if="mode === 'year'"
          v-model="selectedYear"
          type="year"
          value-format="YYYY"
          style="width:100%"
          @change="loadDashboard"
        />
        <el-date-picker
          v-else
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width:100%"
          @change="loadDashboard"
        />
      </div>
      <div class="stats-scope-card">
        <span>当前统计</span>
        <strong>{{ periodLabel }}</strong>
        <small>{{ selectedBookName }}</small>
      </div>
      <div class="stats-total-card">
        <div class="ledger-summary-item expense">
          <span>支出</span>
          <strong>{{ signedMoney(summary.expense, 'EXPENSE') }}</strong>
        </div>
        <div class="ledger-summary-item income">
          <span>收入</span>
          <strong>{{ signedMoney(summary.income, 'INCOME') }}</strong>
        </div>
        <div class="ledger-summary-item balance">
          <span>结余</span>
          <strong>{{ signedMoney(balance, 'BALANCE') }}</strong>
        </div>
      </div>
      <div class="stats-book-card">
        <div class="ledger-panel-title">
          <strong>统计范围</strong>
        </div>
        <el-radio-group v-model="selectedBookId" class="stats-book-radio-group" @change="loadDashboard">
          <el-radio :value="undefined">全部账本</el-radio>
          <el-radio v-for="book in books" :key="book.id" :value="book.id">{{ book.bookName }}</el-radio>
        </el-radio-group>
      </div>
    </aside>

    <main class="stats-main">
      <section class="ledger-panel stats-trend-card">
        <div class="stats-card-head">
          <h3>收支趋势</h3>
          <div class="stats-legend">
            <span class="income">收入</span>
            <span class="expense">支出</span>
          </div>
        </div>
        <v-chart :option="trendOption" autoresize style="height:180px" />
      </section>

      <section class="stats-grid">
        <category-stat-card
          title="支出分类"
          type="EXPENSE"
          :rows="expenseRows"
          :option="expensePieOption"
          :include-children="includeChildren"
          :filter-small="filterSmall"
          @toggle-children="setIncludeChildren"
          @toggle-small="setFilterSmall"
          @open="openDetail"
        />
        <category-detail-card title="支出明细" type="EXPENSE" :rows="expenseRows" @open="openDetail" />
        <category-stat-card
          title="收入分类"
          type="INCOME"
          :rows="incomeRows"
          :option="incomePieOption"
          :include-children="includeChildren"
          :filter-small="filterSmall"
          @toggle-children="setIncludeChildren"
          @toggle-small="setFilterSmall"
          @open="openDetail"
        />
        <category-detail-card title="收入明细" type="INCOME" :rows="incomeRows" @open="openDetail" />
      </section>
    </main>

    <aside v-if="mode !== 'custom'" class="ledger-panel stats-report">
      <div class="stats-card-head">
        <h3>{{ reportTitle }}</h3>
        <el-tag size="small">{{ trendRows.length }}条</el-tag>
      </div>
      <div class="stats-report-head">
        <span>日期</span>
        <span>收入</span>
        <span>支出</span>
        <span>结余</span>
      </div>
      <div class="stats-report-list">
        <div v-for="row in trendRows" :key="row.day" class="stats-report-row">
          <strong>{{ dayLabel(row.day) }}</strong>
          <span class="income">{{ Number(row.income || 0) ? money(row.income) : '-' }}</span>
          <span class="expense">{{ Number(row.expense || 0) ? money(row.expense) : '-' }}</span>
          <span :class="Number(row.income || 0) - Number(row.expense || 0) >= 0 ? 'income' : 'expense'">
            {{ money(Number(row.income || 0) - Number(row.expense || 0)) }}
          </span>
        </div>
      </div>
    </aside>

    <el-drawer v-model="drawerVisible" :title="drawerTitle" size="min(420px, 100vw)">
      <div class="stats-drawer-list" v-loading="detailLoading">
        <button
          v-for="bill in detailBills.records"
          :key="bill.id"
          type="button"
          class="stats-drawer-item"
          @click="openBillDialog(bill)"
        >
          <div class="stats-drawer-icon">
            <el-icon><component :is="categoryIconByBill(bill)" /></el-icon>
          </div>
          <div>
            <strong>{{ categoryNameMap[bill.categoryId] || drawerTitle.replace(/^.*- /, '') }}</strong>
            <span>{{ bill.billDate }} · {{ bill.remark || bill.merchantName || '暂无备注' }}</span>
          </div>
          <em :class="bill.billType === 'EXPENSE' ? 'expense' : 'income'">
            {{ bill.billType === 'EXPENSE' ? '-' : '+' }}{{ money(bill.amount) }}
          </em>
        </button>
        <el-empty v-if="!detailBills.records.length && !detailLoading" description="暂无明细" />
      </div>
      <div v-if="detailBills.total > detailPage.pageSize" class="stats-drawer-pagination">
        <el-pagination
          v-model:current-page="detailPage.pageNum"
          v-model:page-size="detailPage.pageSize"
          small
          background
          layout="prev, pager, next"
          :total="detailBills.total"
          @current-change="loadDetailBills"
        />
      </div>
    </el-drawer>

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
      <div v-if="selectedDetailBill" class="stats-bill-detail">
        <div class="stats-bill-hero" :class="selectedDetailBill.billType === 'EXPENSE' ? 'expense' : 'income'">
          <div class="stats-bill-icon">
            <el-icon><component :is="categoryIconByBill(selectedDetailBill)" /></el-icon>
          </div>
          <div>
            <strong>{{ categoryNameMap[selectedDetailBill.categoryId] || activeCategory?.name || '未分类' }}</strong>
            <span>{{ selectedDetailBill.billDate }}</span>
          </div>
          <em>{{ selectedDetailBill.billType === 'EXPENSE' ? '-' : '+' }}{{ money(selectedDetailBill.amount) }}</em>
        </div>

        <div class="stats-bill-section">
          <div><span>账单分类</span><strong>{{ categoryNameMap[selectedDetailBill.categoryId] || activeCategory?.name || '未分类' }}</strong></div>
          <div><span>归属账本</span><strong>{{ bookNameMap[selectedDetailBill.bookId] || '未知账本' }}</strong></div>
          <div><span>收支账户</span><strong>{{ accountNameMap[selectedDetailBill.accountId] || '未知账户' }}</strong></div>
          <div><span>商户来源</span><strong>{{ selectedDetailBill.merchantName || '未填写' }}</strong></div>
          <div><span>备注</span><strong>{{ selectedDetailBill.remark || '暂无备注' }}</strong></div>
          <div><span>记录方式</span><strong>{{ sourceTypeLabel(selectedDetailBill.sourceType) }}</strong></div>
        </div>

        <div class="stats-bill-section">
          <div><span>账单日期</span><strong>{{ selectedDetailBill.billDate || '-' }}</strong></div>
          <div><span>账单类型</span><strong>{{ selectedDetailBill.billType === 'EXPENSE' ? '支出' : '收入' }}</strong></div>
          <div><span>账单编号</span><strong>#{{ selectedDetailBill.id }}</strong></div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, reactive, ref, watch } from 'vue'
import { ElCheckbox, ElEmpty, ElIcon } from 'element-plus'
import {
  Basketball,
  Bowl,
  Briefcase,
  Brush,
  Cellphone,
  Coin,
  Coffee,
  CreditCard,
  Dish,
  Document,
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
  Present,
  Printer,
  Reading,
  Refrigerator,
  School,
  ShoppingBag,
  Service,
  SuitcaseLine,
  Ticket,
  Tickets,
  Tools,
  User,
  Van,
  Wallet
} from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import request from '@/utils/request'
import { useBookContext } from '@/composables/useBookContext'

const colors = ['#ffc107', '#ff8a3d', '#ff5252', '#f84f92', '#f5d921', '#27b99a', '#1f7af0', '#9b7bff', '#00a6c8']
const modeOptions = [
  { label: '月统计', value: 'month' },
  { label: '年统计', value: 'year' },
  { label: '自定义', value: 'custom' }
]
const { books, currentBookId, loadBooks } = useBookContext()
const dashboard = ref<any>({ summary: {}, expenseCategories: [], incomeCategories: [], trend: [] })
const mode = ref('month')
const selectedBookId = ref<number>()
const selectedMonth = ref(currentMonth())
const selectedYear = ref(String(new Date().getFullYear()))
const dateRange = ref<string[]>(currentMonthRange())
const includeChildren = ref(false)
const filterSmall = ref(false)
const drawerVisible = ref(false)
const drawerTitle = ref('')
const detailLoading = ref(false)
const activeCategory = ref<any>()
const activeDetailType = ref('EXPENSE')
const detailBills = ref<{ total: number; records: any[] }>({ total: 0, records: [] })
const billDialogVisible = ref(false)
const selectedDetailBill = ref<any>()
const accounts = ref<any[]>([])
const categories = ref<any[]>([])
const detailPage = reactive({
  pageNum: 1,
  pageSize: 20
})

const summary = computed(() => dashboard.value.summary || {})
const trendRows = computed(() => dashboard.value.trend || [])
const balance = computed(() => Number(summary.value.income || 0) - Number(summary.value.expense || 0))
const expenseRows = computed(() => normalizeRows(dashboard.value.expenseCategories || []))
const incomeRows = computed(() => normalizeRows(dashboard.value.incomeCategories || []))
const selectedBookName = computed(() =>
  selectedBookId.value
    ? books.value.find((item) => item.id === selectedBookId.value)?.bookName || '当前账本'
    : '全部账本'
)
const periodLabel = computed(() => {
  if (mode.value === 'year') return `${selectedYear.value || '当前'}年`
  if (mode.value === 'custom') {
    const [start, end] = dateRange.value || []
    return start && end ? `${start} 至 ${end}` : '请选择日期范围'
  }
  return selectedMonth.value ? selectedMonth.value.replace('-', '年') + '月' : '请选择月份'
})
const reportTitle = computed(() => mode.value === 'year' ? '月报表' : '日报表')
const bookNameMap = computed(() =>
  Object.fromEntries(books.value.map((item) => [item.id, item.bookName]))
)
const accountNameMap = computed(() =>
  Object.fromEntries(accounts.value.map((item) => [item.id, item.accountName]))
)
const categoryNameMap = computed(() =>
  Object.fromEntries(categories.value.map((item) => [item.id, item.categoryName]))
)
const categoryItemMap = computed(() =>
  Object.fromEntries(categories.value.map((item) => [item.id, item]))
)

const trendOption = computed(() => ({
  color: ['#2db297', '#ff5252'],
  tooltip: { trigger: 'axis' },
  grid: { left: 8, right: 12, top: 20, bottom: 28, containLabel: true },
  xAxis: { type: 'category', data: trendRows.value.map((item: any) => dayLabel(item.day)) },
  yAxis: {
    type: 'value',
    axisLabel: {
      margin: 10,
      width: 64,
      overflow: 'truncate',
      formatter: (value: number) => Number(value || 0).toLocaleString()
    },
    splitLine: { lineStyle: { color: '#edf1f4' } }
  },
  series: [
    { name: '收入', type: 'line', smooth: true, data: trendRows.value.map((item: any) => item.income || 0), symbolSize: 6 },
    { name: '支出', type: 'line', smooth: true, data: trendRows.value.map((item: any) => item.expense || 0), symbolSize: 6 }
  ]
}))
const expensePieOption = computed(() => pieOption(expenseRows.value))
const incomePieOption = computed(() => pieOption(incomeRows.value))

function normalizeRows(rows: any[]) {
  const total = rows.reduce((sum, item) => sum + Number(item.value || 0), 0)
  return rows
    .map((item, index) => ({
      ...item,
      value: Number(item.value || 0),
      count: Number(item.count || 0),
      color: colors[index % colors.length],
      percent: total ? Number(((Number(item.value || 0) / total) * 100).toFixed(1)) : 0
    }))
    .filter((item) => !filterSmall.value || item.percent >= 1)
}

function pieOption(rows: any[]) {
  const total = rows.reduce((sum, item) => sum + Number(item.value || 0), 0)
  return {
    color: rows.map((item) => item.color),
    tooltip: {
      trigger: 'item',
      confine: false,
      appendToBody: true,
      formatter: '{b}<br/>金额: {c}<br/>占比: {d}%'
    },
    graphic: {
      type: 'group',
      left: 'center',
      top: '45%',
      children: [
        {
          type: 'text',
          left: 'center',
          style: {
            text: money(total),
            fill: '#17252e',
            fontSize: 18,
            fontWeight: 800,
            textAlign: 'center'
          }
        },
        {
          type: 'text',
          left: 'center',
          top: 24,
          style: {
            text: '合计',
            fill: '#8f9cab',
            fontSize: 12,
            textAlign: 'center'
          }
        }
      ]
    },
    series: [{
      type: 'pie',
      radius: ['42%', '76%'],
      center: ['50%', '52%'],
      minAngle: 3,
      padAngle: 1,
      itemStyle: {
        borderColor: '#fff',
        borderWidth: 2
      },
      selectedMode: 'single',
      label: { show: false },
      emphasis: {
        scale: true,
        scaleSize: 8
      },
      data: rows.map((item) => ({ ...item, selected: activeCategory.value?.name === item.name }))
    }]
  }
}

function money(value: number | string) {
  return Number(value || 0).toFixed(2)
}

function categoryIcon(item: any) {
  const key = String(item?.icon || item?.name || '').toLowerCase()
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

function signedMoney(value: number | string, type: 'EXPENSE' | 'INCOME' | 'BALANCE') {
  const number = Number(value || 0)
  if (type === 'EXPENSE') return `-${money(Math.abs(number))}`
  if (type === 'INCOME') return `+${money(Math.abs(number))}`
  return `${number >= 0 ? '+' : '-'}${money(Math.abs(number))}`
}

function dayLabel(value?: string) {
  if (!value) return '-'
  if (mode.value === 'year') return `${Number(value.split('-').pop())}月`
  return `${Number(value.split('-').pop())}日`
}

function queryParams() {
  const params: any = {
    mode: mode.value,
    bookId: selectedBookId.value,
    includeChildren: includeChildren.value
  }
  if (mode.value === 'month') params.month = selectedMonth.value
  if (mode.value === 'year') params.year = selectedYear.value
  if (mode.value === 'custom') {
    params.startDate = dateRange.value?.[0]
    params.endDate = dateRange.value?.[1]
  }
  return params
}

function handleModeChange() {
  activeCategory.value = undefined
  if (mode.value === 'month' && !selectedMonth.value) selectedMonth.value = currentMonth()
  if (mode.value === 'year' && !selectedYear.value) selectedYear.value = String(new Date().getFullYear())
  if (mode.value === 'custom' && (!dateRange.value || dateRange.value.length !== 2)) {
    dateRange.value = currentMonthRange()
  }
  loadDashboard()
}

function currentMonth() {
  const date = new Date()
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

function currentMonthRange() {
  const date = new Date()
  const start = new Date(date.getFullYear(), date.getMonth(), 1)
  const end = new Date(date.getFullYear(), date.getMonth() + 1, 0)
  return [formatDate(start), formatDate(end)]
}

function formatDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function setIncludeChildren(value: boolean) {
  includeChildren.value = value
  activeCategory.value = undefined
  loadDashboard()
}

function setFilterSmall(value: boolean) {
  filterSmall.value = value
  activeCategory.value = undefined
}

async function loadDashboard() {
  if (mode.value === 'month' && !selectedMonth.value) return
  if (mode.value === 'year' && !selectedYear.value) return
  if (mode.value === 'custom' && (!dateRange.value?.[0] || !dateRange.value?.[1])) return
  activeCategory.value = undefined
  dashboard.value = await request.get('/api/statistics/dashboard', { params: queryParams() })
}

async function openDetail(row: any, type: string) {
  activeCategory.value = row
  activeDetailType.value = type
  drawerTitle.value = `${type === 'EXPENSE' ? '支出' : '收入'} - ${row.name}`
  drawerVisible.value = true
  detailPage.pageNum = 1
  await loadDetailBills()
}

async function loadDetailBills() {
  if (!activeCategory.value) return
  detailLoading.value = true
  try {
    await loadDetailDictionaries()
    const row = activeCategory.value
    const params: any = {
      ...queryParams(),
      pageNum: detailPage.pageNum,
      pageSize: detailPage.pageSize,
      billType: activeDetailType.value
    }
    delete params.mode
    delete params.includeChildren
    if (mode.value === 'month') params.month = selectedMonth.value
    if (mode.value === 'year') {
      params.startDate = `${selectedYear.value}-01-01`
      params.endDate = `${selectedYear.value}-12-31`
    }
    if (includeChildren.value || row.parentId) {
      params.categoryId = row.categoryId
    } else {
      params.parentCategoryId = row.categoryId
    }
    detailBills.value = await request.get('/api/bills', { params })
  } finally {
    detailLoading.value = false
  }
}

async function loadDetailDictionaries() {
  const [expenseCategories, incomeCategories] = await Promise.all([
    request.get('/api/categories', { params: { bookId: selectedBookId.value || currentBookId.value, categoryType: 'EXPENSE' } }),
    request.get('/api/categories', { params: { bookId: selectedBookId.value || currentBookId.value, categoryType: 'INCOME' } })
  ])
  categories.value = [...expenseCategories, ...incomeCategories]

  const targetBooks = selectedBookId.value
    ? books.value.filter((item) => item.id === selectedBookId.value)
    : books.value
  const accountGroups = await Promise.all(
    targetBooks.map((book) => request.get('/api/accounts', { params: { bookId: book.id } }))
  )
  const merged = accountGroups.flat()
  accounts.value = Array.from(new Map(merged.map((item: any) => [item.id, item])).values())
}

function openBillDialog(bill: any) {
  selectedDetailBill.value = bill
  billDialogVisible.value = true
}

function categoryIconByBill(bill: any) {
  return categoryIcon(categoryItemMap.value[bill.categoryId] || activeCategory.value || {})
}

function sourceTypeLabel(value?: string) {
  const normalized = String(value || '').trim().toUpperCase()
  if (normalized === 'AUTO' || value === '自动记账') return '自动记账'
  if (normalized === 'OCR' || value === 'OCR识别' || value === 'OCR导入') return 'OCR识别'
  if (normalized === 'IMPORT' || normalized === 'EXCEL' || value === 'Excel导入') return '批量导入'
  return '手动记账'
}

const CategoryStatCard = defineComponent({
  props: ['title', 'type', 'rows', 'option', 'includeChildren', 'filterSmall'],
  emits: ['toggleChildren', 'toggleSmall', 'open'],
  setup(props, { emit }) {
    return () => h('div', { class: 'ledger-panel stats-card' }, [
      h('div', { class: 'stats-card-head' }, [h('h3', props.title)]),
      props.rows.length
        ? h('div', { class: 'stats-chart-row' }, [
          h('div', { class: 'stats-pie-wrap' }, [
            h(VChart, { option: props.option, autoresize: true, style: 'height:220px' })
          ]),
          h('div', { class: 'stats-rank-list' }, props.rows.map((item: any) =>
            h('button', { class: 'stats-rank-item', onClick: () => emit('open', item, props.type) }, [
              h('span', { style: { background: item.color } }),
              h('strong', item.name),
              h('em', `${props.type === 'EXPENSE' ? '-' : '+'}${money(item.value)}`),
              h('small', `${item.percent}%`)
            ])
          ))
        ])
        : h(ElEmpty, { description: '暂无数据' }),
      h('div', { class: 'stats-card-options' }, [
        h(ElCheckbox, { modelValue: props.includeChildren, onChange: (value: unknown) => emit('toggleChildren', value === true) }, () => '二级分类'),
        h(ElCheckbox, { modelValue: props.filterSmall, onChange: (value: unknown) => emit('toggleSmall', value === true) }, () => '过滤<1%')
      ])
    ])
  }
})

const CategoryDetailCard = defineComponent({
  props: ['title', 'type', 'rows'],
  emits: ['open'],
  setup(props, { emit }) {
    return () => h('div', { class: 'ledger-panel stats-card stats-fixed-card' }, [
      h('h3', props.title),
      h('div', { class: 'stats-detail-list' }, props.rows.map((item: any) =>
        h('button', { class: props.type === 'EXPENSE' ? 'is-expense' : 'is-income', onClick: () => emit('open', item, props.type) }, [
          h('span', { class: 'stats-detail-color', style: { background: item.color } }),
          h('span', { class: 'stats-detail-icon-bubble' }, [
            h(ElIcon, null, () => h(categoryIcon(item)))
          ]),
          h('span', { class: 'stats-detail-copy' }, [
            h('strong', item.name),
            h('small', `${item.count}笔`)
          ]),
          h('em', { class: props.type === 'EXPENSE' ? 'expense' : 'income' }, `${props.type === 'EXPENSE' ? '-' : '+'}${money(item.value)}`)
        ])
      )),
      !props.rows.length ? h(ElEmpty, { description: '暂无数据' }) : null
    ])
  }
})

watch(currentBookId, (value) => {
  selectedBookId.value = value
  loadDashboard()
})

onMounted(async () => {
  await loadBooks()
  selectedBookId.value = currentBookId.value
  await loadDashboard()
})
</script>
