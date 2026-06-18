<template>
  <div class="manage-shell">
    <div class="manage-hero">
      <div>
        <div class="manage-section-eyebrow">Budgets</div>
        <h2>预算管理</h2>
        <p>按月份管理总预算和分类预算，及时发现超支风险，并和消息提醒联动处理。</p>
      </div>
      <div class="manage-actions">
        <el-button :icon="Bell" @click="router.push('/messages')">查看提醒</el-button>
        <el-button class="manage-primary-button" :icon="Plus" type="primary" @click="openCreateDialog">新增预算</el-button>
      </div>
    </div>

    <div class="manage-workbench">
      <div ref="summaryRef" class="manage-stats" :class="{ 'budget-focus-ring': focusSection === 'total' }">
        <div class="manage-stat">
          <div class="manage-stat-label">预算条目</div>
          <div class="manage-stat-value">{{ filteredBudgets.length }}</div>
        </div>
        <div class="manage-stat">
          <div class="manage-stat-label">预算总额</div>
          <div class="manage-stat-value">{{ formatCurrency(totalBudget) }}</div>
        </div>
        <div class="manage-stat">
          <div class="manage-stat-label">已用金额</div>
          <div class="manage-stat-value">{{ formatCurrency(usedBudget) }}</div>
        </div>
        <div class="manage-stat">
          <div class="manage-stat-label">剩余可用</div>
          <div class="manage-stat-value" :class="remainingBudget < 0 ? 'manage-negative' : 'manage-positive'">
            {{ formatSignedCurrency(remainingBudget) }}
          </div>
        </div>
        <div class="manage-stat">
          <div class="manage-stat-label">预警条目</div>
          <div class="manage-stat-value">{{ warningBudgets.length }}</div>
        </div>
      </div>

      <div class="manage-control-card">
        <div class="manage-control-row">
          <div class="manage-control-primary">
            <el-input v-model="filters.keyword" clearable class="manage-search" placeholder="搜索预算分类" :prefix-icon="Search" />
          </div>
          <div class="manage-control-actions">
            <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
            <el-button class="manage-danger-button" :disabled="!selectedIds.length" :icon="Delete" :loading="loading.deleting" type="danger" plain @click="removeSelectedBudgets">删除</el-button>
          </div>
        </div>
        <div class="manage-filter-grid" style="margin-top:0.75rem">
          <el-select v-model="selectedMonth" placeholder="预算月份">
            <el-option v-for="item in monthOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </div>
      </div>

      <div ref="alertRef" class="page-card budget-alert-card" :class="{ 'budget-focus-ring': focusSection === 'warning' }">
        <div class="budget-alert-head">
          <div>
            <div class="manage-primary-title">预算提醒</div>
            <div class="manage-subtle-text">
              当前月份 {{ formatBudgetMonth(selectedMonth) }}，未读消息 {{ notificationStore.unreadCount }} 条
            </div>
          </div>
          <el-tag :type="warningBudgets.length ? 'danger' : 'success'">
            {{ warningBudgets.length ? `超支/临界 ${warningBudgets.length} 项` : '预算状态正常' }}
          </el-tag>
        </div>
        <div v-if="warningBudgets.length" class="budget-warning-list">
          <button
            v-for="item in warningBudgets.slice(0, 4)"
            :key="item.id"
            type="button"
            class="budget-warning-item"
            @click="openEditDialog(item)"
          >
            <div class="budget-warning-main">
              <strong>{{ item.categoryId ? categoryNameMap[item.categoryId] || '关联分类已删除' : '月总预算' }}</strong>
              <span>{{ item.progress >= 100 ? '已超支' : '即将触达预算上限' }}</span>
            </div>
            <div class="budget-warning-side">
              <em :class="item.progress >= 100 ? 'manage-negative' : ''">{{ item.progress }}%</em>
              <small>{{ formatCurrency(item.usedAmount) }} / {{ formatCurrency(item.budgetAmount) }}</small>
            </div>
          </button>
        </div>
        <el-empty v-else description="本月还没有需要特别关注的预算项" :image-size="72" />
      </div>

      <div class="manage-table-card">
        <div class="manage-toolbar">
          <div>
            <h3 style="margin:0">预算列表</h3>
            <div class="manage-subtle-text" style="margin-top:0.375rem">查看月总预算和分类预算的执行情况</div>
          </div>
          <div class="manage-toolbar-group">
            <span class="manage-subtle-text">已选 {{ selectedIds.length }} 条</span>
          </div>
        </div>
        <el-table ref="tableRef" :data="pagedBudgets" empty-text="暂无预算数据" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="52" />
        <el-table-column label="月份" width="140">
          <template #default="{ row }">{{ formatBudgetMonth(row.budgetMonth) }}</template>
        </el-table-column>
        <el-table-column label="预算分类" min-width="220">
          <template #default="{ row }">
            <div class="manage-primary-cell">
              <div class="manage-primary-title">
                {{ row.categoryId ? categoryNameMap[row.categoryId] || '关联分类已删除' : '月总预算' }}
              </div>
              <div class="manage-primary-subtitle">{{ row.categoryId ? '分类预算' : '全月总控' }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="预算金额" width="140" align="right">
          <template #default="{ row }">
            <span class="manage-amount">{{ formatCurrency(row.budgetAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="已用金额" width="140" align="right">
          <template #default="{ row }">
            <span class="manage-amount" :class="calcProgress(row.usedAmount, row.budgetAmount) > 100 ? 'is-expense' : ''">
              {{ formatCurrency(row.usedAmount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="执行率" min-width="160">
          <template #default="{ row }">
            <div class="manage-progress-cell">
              <el-progress
                :percentage="Math.min(row.progress, 100)"
                :show-text="false"
                :status="row.progress > 100 ? 'exception' : undefined"
              />
              <span :class="row.progress > 100 ? 'manage-negative' : 'manage-subtle-text'">
                {{ row.progress }}%
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="剩余金额" width="150" align="right">
          <template #default="{ row }">
            <span :class="row.remainingAmount < 0 ? 'manage-negative' : 'manage-positive'">
              {{ formatSignedCurrency(row.remainingAmount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="budgetStatusType(row.progress)">
              {{ budgetStatusLabel(row.progress) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <div class="manage-action-links">
              <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
              <el-button link type="danger" @click="removeBudget(row.id)">删除</el-button>
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
            :total="filteredBudgets.length"
            @current-change="clearSelection"
            @size-change="handlePageSizeChange"
          />
        </div>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑预算' : '新增预算'" width="min(47.5rem, calc(100vw - 1.5rem))">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="budget-dialog-form">
        <div class="budget-dialog-intro">
          <strong>{{ form.id ? '调整预算方案' : '新建预算方案' }}</strong>
          <span>总预算适合整月控额，分类预算适合对重点消费项单独预警。</span>
        </div>

        <div class="budget-dialog-grid">
          <section class="budget-dialog-card">
            <div class="budget-dialog-card-head">
              <strong>基础信息</strong>
              <small>先确定预算月份和金额</small>
            </div>
            <div class="budget-dialog-fields">
              <el-form-item label="预算月份" prop="budgetMonth">
                <el-select v-model="form.budgetMonth" style="width:100%">
                  <el-option v-for="item in monthOptions" :key="item" :label="item" :value="item" />
                </el-select>
              </el-form-item>
              <el-form-item label="预算金额" prop="budgetAmount">
                <el-input-number v-model="form.budgetAmount" :min="0.01" :precision="2" style="width:100%" />
              </el-form-item>
            </div>
          </section>

          <section class="budget-dialog-card budget-dialog-card-wide">
            <div class="budget-dialog-card-head">
              <strong>预算范围</strong>
              <small>{{ form.categoryId ? `已选：${selectedBudgetCategoryPath(form.categoryId)}` : '当前为月总预算' }}</small>
            </div>
            <el-form-item label="预算分类" prop="categoryId" class="budget-dialog-category-item">
            <div class="budget-category-picker">
              <button
                type="button"
                class="budget-total-option"
                :class="{ active: !form.categoryId }"
                @click="selectBudgetTotal"
              >
                <span class="budget-total-icon">总</span>
                <div>
                  <strong>月总预算</strong>
                  <small>不限制分类，整月统一控额</small>
                </div>
              </button>

              <div class="budget-category-block">
                <div class="budget-category-grid budget-category-primary-grid">
                  <template v-for="row in budgetPrimaryRows" :key="row.map((item) => item.id).join('-')">
                    <template v-for="item in row" :key="item.id">
                      <button
                        type="button"
                        :class="{ active: isBudgetPrimaryActive(item) }"
                        @click="selectBudgetPrimaryCategory(item)"
                      >
                        <span><el-icon><component :is="categoryIcon(item)" /></el-icon></span>
                        {{ item.categoryName }}
                      </button>
                    </template>
                    <div
                      v-if="selectedBudgetRowChildren(row).length"
                      class="budget-subcategory-panel budget-subcategory-inline"
                    >
                      <button
                        v-for="child in selectedBudgetRowChildren(row)"
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
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading.saving" @click="saveBudget">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import {
  Basketball,
  Bell,
  Bowl,
  Briefcase,
  Brush,
  Cellphone,
  Coin,
  Coffee,
  CreditCard,
  Delete,
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
  Plus,
  Present,
  Printer,
  Reading,
  Refrigerator,
  Refresh,
  School,
  Search,
  Service,
  ShoppingBag,
  SuitcaseLine,
  Ticket,
  Tools,
  User,
  Van,
  Wallet
} from '@element-plus/icons-vue'
import type { FormInstance, FormRules, TableInstance } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useBookContext } from '@/composables/useBookContext'
import { useNotificationStore } from '@/stores/notification'
import request from '@/utils/request'

const router = useRouter()
const route = useRoute()
const notificationStore = useNotificationStore()
const monthOptions = computed(() => buildMonthOptions(12))
const budgets = ref<any[]>([])
const expenseCategories = ref<any[]>([])
const selectedIds = ref<number[]>([])
const dialogVisible = ref(false)
const tableRef = ref<TableInstance>()
const formRef = ref<FormInstance>()
const summaryRef = ref<HTMLElement>()
const alertRef = ref<HTMLElement>()
const selectedMonth = ref(currentMonth())
const focusSection = ref<'total' | 'warning' | ''>('')
const { currentBookId, loadBooks } = useBookContext()

const pageState = reactive({
  pageNum: 1,
  pageSize: 10
})
const loading = reactive({
  saving: false,
  deleting: false
})

const filters = reactive({
  keyword: ''
})

const emptyForm = () => ({
  id: undefined as number | undefined,
  bookId: currentBookId.value,
  categoryId: undefined as number | undefined,
  budgetMonth: selectedMonth.value,
  budgetAmount: 5000
})
const form = reactive(emptyForm())

const rules: FormRules = {
  budgetMonth: [{ required: true, message: '请选择预算月份', trigger: 'change' }],
  budgetAmount: [{ required: true, message: '请输入预算金额', trigger: 'change' }]
}

const filteredBudgets = computed(() =>
  normalizedBudgets.value.filter((item) => {
    const keyword = filters.keyword.trim().toLowerCase()
    const categoryLabel = item.categoryId ? categoryNameMap.value[item.categoryId] || '' : '月总预算'
    return !keyword || categoryLabel.toLowerCase().includes(keyword)
  })
)
const normalizedBudgets = computed(() =>
  budgets.value.map((item) => {
    const progress = calcProgress(item.usedAmount, item.budgetAmount)
    return {
      ...item,
      progress,
      remainingAmount: Number(item.budgetAmount || 0) - Number(item.usedAmount || 0)
    }
  })
)
const pagedBudgets = computed(() => {
  const start = (pageState.pageNum - 1) * pageState.pageSize
  return filteredBudgets.value.slice(start, start + pageState.pageSize)
})
const categoryNameMap = computed(() =>
  Object.fromEntries(expenseCategories.value.map((item) => [item.id, item.categoryName]))
)
const totalBudget = computed(() =>
  filteredBudgets.value.reduce((sum, item) => sum + Number(item.budgetAmount || 0), 0)
)
const usedBudget = computed(() =>
  filteredBudgets.value.reduce((sum, item) => sum + Number(item.usedAmount || 0), 0)
)
const remainingBudget = computed(() => Number(totalBudget.value || 0) - Number(usedBudget.value || 0))
const warningBudgets = computed(() =>
  filteredBudgets.value
    .filter((item) => item.progress >= 80)
    .sort((a, b) => b.progress - a.progress)
)
const budgetPrimaryCategories = computed(() =>
  expenseCategories.value.filter((item) => !item.parentId || Number(item.parentId) === 0)
)
const budgetPrimaryRows = computed(() => chunkCategories(budgetPrimaryCategories.value, 4))
const selectedBudgetParentId = ref<number>()

function formatCurrency(value: number | string) {
  return `¥${Number(value || 0).toFixed(2)}`
}

function formatSignedCurrency(value: number | string) {
  const amount = Number(value || 0)
  return `${amount >= 0 ? '+' : '-'}¥${Math.abs(amount).toFixed(2)}`
}

function formatBudgetMonth(value?: string) {
  return value ? value.replace('-', ' 年 ') + ' 月' : '-'
}

function currentMonth() {
  const date = new Date()
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

function buildMonthOptions(count: number) {
  const base = new Date()
  return Array.from({ length: count }, (_, index) => {
    const date = new Date(base.getFullYear(), base.getMonth() - index, 1)
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
  })
}

function calcProgress(usedAmount: number, budgetAmount: number) {
  if (!budgetAmount) return 0
  return Math.round((Number(usedAmount || 0) / Number(budgetAmount || 0)) * 100)
}

function formatProgress(usedAmount: number, budgetAmount: number) {
  return `${calcProgress(usedAmount, budgetAmount)}%`
}

function budgetStatusLabel(progress: number) {
  if (progress >= 100) return '已超支'
  if (progress >= 80) return '预警中'
  return '正常'
}

function budgetStatusType(progress: number) {
  if (progress >= 100) return 'danger'
  if (progress >= 80) return 'warning'
  return 'success'
}

function chunkCategories(list: any[], size: number) {
  const rows: any[][] = []
  for (let index = 0; index < list.length; index += size) {
    rows.push(list.slice(index, index + size))
  }
  return rows
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

function budgetChildrenByParent(parentId: number) {
  return expenseCategories.value.filter((item) => Number(item.parentId || 0) === parentId)
}

function selectedBudgetCategoryPath(categoryId?: number) {
  if (!categoryId) return '月总预算'
  const current = expenseCategories.value.find((item) => item.id === categoryId)
  if (!current) return categoryNameMap.value[categoryId] || '已选分类'
  const parentId = Number(current.parentId || 0)
  if (!parentId) {
    return current.categoryName
  }
  const parent = expenseCategories.value.find((item) => item.id === parentId)
  return parent ? `${parent.categoryName} / ${current.categoryName}` : current.categoryName
}

function syncBudgetParent() {
  if (!form.categoryId) {
    selectedBudgetParentId.value = undefined
    return
  }
  const current = expenseCategories.value.find((item) => item.id === form.categoryId)
  if (!current) {
    selectedBudgetParentId.value = undefined
    return
  }
  if (current.parentId && Number(current.parentId) !== 0) {
    selectedBudgetParentId.value = Number(current.parentId)
    return
  }
  selectedBudgetParentId.value = current.id
}

function selectedBudgetRowChildren(row: any[]) {
  const active = row.find((item) => item.id === selectedBudgetParentId.value)
  if (!active) return []
  const children = budgetChildrenByParent(active.id)
  return children.length ? children : [active]
}

function isBudgetPrimaryActive(item: any) {
  return selectedBudgetParentId.value === item.id
}

function selectBudgetPrimaryCategory(item: any) {
  selectedBudgetParentId.value = item.id
  const children = budgetChildrenByParent(item.id)
  form.categoryId = children.length ? children[0].id : item.id
}

function selectBudgetTotal() {
  form.categoryId = undefined
  selectedBudgetParentId.value = undefined
}

function clearSelection() {
  selectedIds.value = []
  tableRef.value?.clearSelection()
}

function handleSelectionChange(selection: any[]) {
  selectedIds.value = selection.map((item) => item.id)
}

function handlePageSizeChange() {
  pageState.pageNum = 1
  clearSelection()
}

function resetForm() {
  Object.assign(form, emptyForm())
  form.bookId = currentBookId.value
  selectedBudgetParentId.value = undefined
}

function resetFilters() {
  selectedMonth.value = currentMonth()
  filters.keyword = ''
  pageState.pageNum = 1
  clearSelection()
}

function openCreateDialog() {
  resetForm()
  form.budgetAmount = warningBudgets.value[0]?.budgetAmount || 5000
  dialogVisible.value = true
}

function openEditDialog(row: any) {
  Object.assign(form, {
    ...emptyForm(),
    ...row,
    categoryId: row.categoryId || undefined,
    budgetAmount: Number(row.budgetAmount || 0)
  })
  syncBudgetParent()
  dialogVisible.value = true
}

async function loadCategories() {
  if (!currentBookId.value) return
  expenseCategories.value = await request.get('/api/categories', {
    params: { bookId: currentBookId.value, categoryType: 'EXPENSE' }
  })
}

async function loadBudgets() {
  if (!currentBookId.value) return
  budgets.value = await request.get('/api/budgets', {
    params: { bookId: currentBookId.value, budgetMonth: selectedMonth.value }
  })
  if ((pageState.pageNum - 1) * pageState.pageSize >= filteredBudgets.value.length && pageState.pageNum > 1) {
    pageState.pageNum -= 1
  }
  clearSelection()
}

async function applyRouteFocus() {
  const month = typeof route.query.month === 'string' ? route.query.month : ''
  const focus = typeof route.query.focus === 'string' ? route.query.focus : ''
  const fromMessage = typeof route.query.fromMessage === 'string' ? route.query.fromMessage : ''

  if (month && month !== selectedMonth.value) {
    selectedMonth.value = month
    return
  }

  if (!focus) {
    focusSection.value = ''
    return
  }

  focusSection.value = focus === 'warning' ? 'warning' : 'total'
  await nextTick()
  const target = focusSection.value === 'warning' ? alertRef.value : summaryRef.value
  target?.scrollIntoView({ behavior: 'smooth', block: 'start' })

  if (fromMessage) {
    ElMessage.success(`已定位到 ${formatBudgetMonth(selectedMonth.value)} 的预算视图`)
  }
}

async function saveBudget() {
  if (loading.saving) return
  form.bookId = currentBookId.value
  if (!form.id && form.categoryId === undefined && expenseCategories.value.length) {
    form.categoryId = undefined
  }
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.saving = true
  try {
    if (form.id) {
      await request.put(`/api/budgets/${form.id}`, form)
    } else {
      await request.post('/api/budgets', form)
    }
    ElMessage.success(form.id ? '预算已更新' : '预算已新增')
    dialogVisible.value = false
    await loadBudgets()
  } finally {
    loading.saving = false
  }
}

async function removeBudget(budgetId: number) {
  if (loading.deleting) return
  await ElMessageBox.confirm('删除后不可恢复，是否继续？', '删除预算', { type: 'warning' })
  loading.deleting = true
  try {
    await request.delete(`/api/budgets/${budgetId}`)
    ElMessage.success('预算已删除')
    await loadBudgets()
  } finally {
    loading.deleting = false
  }
}

async function removeSelectedBudgets() {
  if (loading.deleting || !selectedIds.value.length) return
  await ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 条预算吗？`, '批量删除预算', {
    type: 'warning'
  })
  loading.deleting = true
  try {
    await request.delete('/api/budgets/batch', { data: { ids: selectedIds.value } })
    ElMessage.success('选中预算已删除')
    await loadBudgets()
  } finally {
    loading.deleting = false
  }
}

watch(selectedMonth, async () => {
  pageState.pageNum = 1
  await loadBudgets()
  await applyRouteFocus()
  if (!dialogVisible.value) return
  form.budgetMonth = selectedMonth.value
})

watch(
  () => filters.keyword,
  () => {
    pageState.pageNum = 1
    clearSelection()
  }
)

watch(
  () => form.categoryId,
  () => {
    syncBudgetParent()
  }
)

watch(currentBookId, async (value, oldValue) => {
  if (!value || value === oldValue) return
  pageState.pageNum = 1
  await loadCategories()
  await loadBudgets()
  await applyRouteFocus()
  resetForm()
}, { immediate: false })

watch(
  () => route.query,
  async () => {
    await applyRouteFocus()
  }
)

onMounted(async () => {
  await loadBooks()
  await notificationStore.loadMessages()
  await loadCategories()
  await loadBudgets()
  await applyRouteFocus()
})
</script>

<style scoped lang="stylus">
.manage-shell
  gap 1rem

.manage-hero
  padding 1.125rem 1.25rem
  border 0.0625rem solid rgba(64, 141, 134, 0.12)
  border-radius 0.5rem
  background linear-gradient(135deg, #ffffff 0%, #f2fbf9 58%, #eef7ff 100%)
  box-shadow 0 0.75rem 1.75rem rgba(29, 50, 61, 0.05)

.manage-hero h2
  font-size 1.625rem
  font-weight 900
  color #132933

.manage-hero p
  max-width 38rem
  color #667985

.manage-section-eyebrow
  display inline-flex
  align-items center
  min-height 1.5rem
  padding 0 0.625rem
  border-radius 62.4375rem
  background #e9f8f5
  color #168a78
  font-weight 800

.manage-workbench
  display grid
  gap 1rem
  background transparent
  border 0
  border-radius 0
  overflow visible

.manage-workbench .manage-stats
  padding 0
  background transparent
  border-bottom 0

.manage-workbench .manage-stat
  min-height 6.25rem
  border 0.0625rem solid rgba(64, 141, 134, 0.1)
  border-radius 0.5rem
  background linear-gradient(180deg, #ffffff 0%, #fbfefd 100%)
  box-shadow 0 0.625rem 1.5rem rgba(29, 50, 61, 0.045)

.manage-stat::after
  content ''
  position absolute
  left 1rem
  right 1rem
  bottom 0
  height 0.1875rem
  border-radius 62.4375rem
  background linear-gradient(90deg, #26a69a, rgba(79, 142, 247, 0.65))

.manage-stat-label
  font-weight 700
  color #7a8d98

.manage-stat-value
  font-size 1.625rem
  font-weight 900
  color #122934

.manage-workbench .manage-control-card,
.budget-alert-card,
.manage-workbench .manage-table-card
  border 0.0625rem solid rgba(64, 141, 134, 0.1)
  border-radius 0.5rem
  background #ffffff
  box-shadow 0 0.625rem 1.5rem rgba(29, 50, 61, 0.045)
  overflow hidden

.manage-workbench .manage-control-card
  padding 1rem

.manage-workbench .manage-table-card
  border-radius 0.5rem

.manage-table-card .manage-toolbar
  padding 1rem 1.125rem
  margin 0
  background linear-gradient(180deg, #fbfefd 0%, #ffffff 100%)
  border-bottom 0.0625rem solid #edf3f2

.manage-table-card :deep(.el-table)
  --el-table-border-color #edf3f2
  --el-table-header-bg-color #f5faf9
  --el-table-row-hover-bg-color #f3fbf9

.manage-table-card :deep(.el-table th.el-table__cell)
  color #5f7580
  font-weight 800

.manage-table-card :deep(.el-table td.el-table__cell),
.manage-table-card :deep(.el-table th.el-table__cell)
  padding-top 0.875rem
  padding-bottom 0.875rem

.manage-table-card .manage-pagination
  margin 0 1.125rem
  padding 0.875rem 0 1rem

.manage-progress-cell :deep(.el-progress-bar__outer)
  background #edf3f2

.manage-progress-cell :deep(.el-progress-bar__inner)
  background linear-gradient(90deg, #26a69a, #4f8ef7)

.budget-focus-ring {
  border-radius: 0.75rem;
  box-shadow: 0 0 0 0.125rem rgba(38, 166, 154, 0.24), 0 0.875rem 1.875rem rgba(38, 166, 154, 0.08);
  transition: box-shadow 0.24s ease;
}

.budget-dialog-form {
  display: grid;
  gap: 1rem;
}

.budget-dialog-intro {
  display: grid;
  gap: 0.375rem;
  padding: 0.875rem 1rem;
  border-radius: 1rem;
  background: linear-gradient(135deg, #f7fcfb 0%, #edf7f5 100%);
  border: 0.0625rem solid rgba(83, 181, 171, 0.16);
}

.budget-dialog-intro strong,
.budget-dialog-card-head strong {
  color: #17252e;
  font-size: 0.9375rem;
  font-weight: 800;
}

.budget-dialog-intro span,
.budget-dialog-card-head small {
  color: #7b8c96;
  font-size: 0.75rem;
  line-height: 1.6;
}

.budget-dialog-grid {
  display: grid;
  gap: 0.875rem;
}

.budget-dialog-card {
  padding: 1rem;
  border-radius: 1.125rem;
  background: #fbfdfd;
  border: 0.0625rem solid rgba(64, 141, 134, 0.1);
}

.budget-dialog-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.625rem;
  margin-bottom: 0.875rem;
  flex-wrap: wrap;
}

.budget-dialog-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.875rem;
}

.budget-dialog-category-item {
  margin-bottom: 0;
}

.budget-alert-card {
  display: grid;
  gap: 0.75rem;
  padding: 1rem 1.125rem;
}

.budget-alert-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.budget-warning-list {
  display: grid;
  gap: 0.625rem;
}

.budget-warning-item {
  width: 100%;
  border: 0.0625rem solid #edf3f2;
  border-radius: 0.625rem;
  background: linear-gradient(180deg, #ffffff 0%, #fbfefd 100%);
  padding: 0.75rem 0.875rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease, transform 0.18s ease;
}

.budget-warning-item:hover {
  border-color: #9bd8d1;
  background: #f3fbf9;
  transform: translateY(-0.0625rem);
}

.budget-warning-main,
.budget-warning-side {
  display: grid;
  gap: 0.25rem;
}

.budget-warning-main strong {
  color: #17252e;
}

.budget-warning-main span,
.budget-warning-side small {
  color: #7b8c96;
  font-size: 0.75rem;
}

.budget-warning-side {
  justify-items: end;
}

.budget-warning-side em {
  font-style: normal;
  font-weight: 800;
  color: #d46b08;
}

.budget-category-picker {
  display: grid;
  gap: 0.75rem;
  width: 100%;
}

.budget-total-option {
  width: 100%;
  border: 0.0625rem solid #dce8e6;
  border-radius: 0.875rem;
  background: #f8fcfb;
  padding: 0.75rem 0.875rem;
  display: grid;
  grid-template-columns: 2.75rem minmax(0, 1fr);
  gap: 0.75rem;
  align-items: center;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease, transform 0.18s ease;
}

.budget-total-option:hover,
.budget-total-option.active {
  border-color: #53b5ab;
  background: #eef8f6;
  transform: translateY(-0.0625rem);
}

.budget-total-icon {
  width: 2.75rem;
  height: 2.75rem;
  border-radius: 0.875rem;
  display: grid;
  place-items: center;
  background: #ffffff;
  color: #169980;
  font-size: 0.9375rem;
  font-weight: 800;
  box-shadow: 0 0.25rem 0.75rem rgba(38, 166, 154, 0.08);
}

.budget-total-option strong,
.budget-category-block-head span {
  color: #17252e;
  font-size: 0.875rem;
  font-weight: 800;
}

.budget-total-option small,
.budget-category-block-head small {
  color: #7b8c96;
  font-size: 0.75rem;
}

.budget-category-block {
  width: 100%;
  border-radius: 1rem;
  background: linear-gradient(180deg, #f7fbfa 0%, #edf6f4 100%);
  padding: 0.875rem;
}

.budget-category-grid {
  display: grid;
  gap: 0.625rem;
}

.budget-category-primary-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.budget-category-primary-grid button,
.budget-subcategory-panel button {
  width: 100%;
  min-width: 0;
  border: 0;
  border-radius: 0.875rem;
  background: transparent;
  min-height: 4.75rem;
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

.budget-category-primary-grid button:hover,
.budget-category-primary-grid button.active,
.budget-subcategory-panel button:hover,
.budget-subcategory-panel button.active {
  background: #ffffff;
  color: #169980;
  transform: translateY(-0.0625rem);
}

.budget-category-primary-grid span,
.budget-subcategory-panel span {
  width: 2.375rem;
  height: 2.375rem;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #ffffff;
  color: inherit;
  box-shadow: 0 0.125rem 0.625rem rgba(29, 50, 61, 0.06);
}

.budget-category-primary-grid .el-icon,
.budget-subcategory-panel .el-icon {
  font-size: 1.25rem;
}

.budget-subcategory-panel {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.625rem;
  padding: 0.75rem;
  align-content: start;
  border-radius: 1rem;
  background: rgba(255, 255, 255, 0.72);
}

@media (max-width: 47.5rem) {
  .budget-dialog-fields,
  .budget-category-primary-grid,
  .budget-subcategory-panel {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .budget-dialog-fields {
    grid-template-columns: 1fr;
  }
}
</style>
