<template>
  <div class="category-manage-shell">
    <section class="category-page-head">
      <div>
        <div class="manage-section-eyebrow">Categories</div>
        <h2>分类管理</h2>
        <p>维护当前账本下的收入、支出分类，支持一级/二级分类、图标、排序和批量删除。</p>
      </div>
      <el-button :icon="Plus" type="primary" @click="openCreateDialog()">新增分类</el-button>
    </section>

    <section class="category-stat-grid">
      <div class="category-stat-card">
        <span>分类总数</span>
        <strong>{{ categories.length }}</strong>
        <small>一级 {{ rootCount }} 个 / 二级 {{ childCount }} 个</small>
      </div>
      <div class="category-stat-card expense">
        <span>支出分类</span>
        <strong>{{ expenseCount }}</strong>
        <small>{{ expenseRootCount }} 个一级分类</small>
      </div>
      <div class="category-stat-card income">
        <span>收入分类</span>
        <strong>{{ incomeCount }}</strong>
        <small>{{ incomeRootCount }} 个一级分类</small>
      </div>
      <div class="category-stat-card selected">
        <span>当前筛选</span>
        <strong>{{ filteredCategories.length }}</strong>
        <small>{{ activeFilterText }}</small>
      </div>
    </section>

    <section class="category-control-card">
      <div class="category-filter-row">
        <el-input
          v-model="filters.keyword"
          clearable
          class="category-search"
          placeholder="搜索分类名称、父级分类"
          :prefix-icon="Search"
        />
        <el-segmented
          v-model="filters.categoryType"
          :options="typeOptions"
          class="category-type-tabs"
        />
        <div class="category-control-actions">
          <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
          <el-button
            :disabled="!selectedIds.length"
            :icon="Delete"
            :loading="loading.deleting"
            type="danger"
            plain
            @click="removeSelectedCategories"
          >
            批量删除
          </el-button>
        </div>
      </div>
    </section>

    <section class="category-workbench">
      <aside class="category-tree-panel">
        <div class="category-panel-head">
          <div>
            <h3>分类结构</h3>
            <span>点击一级分类筛选右侧列表</span>
          </div>
          <el-tag effect="plain">{{ categoryGroups.length }} 组</el-tag>
        </div>

        <button
          type="button"
          class="category-tree-all"
          :class="{ active: !filters.parentId }"
          @click="selectParent()"
        >
          <span>全部分类</span>
          <strong>{{ filteredByTypeCategories.length }}</strong>
        </button>

        <div v-if="!categoryGroups.length" class="category-tree-empty">
          <el-empty description="暂无分类" />
        </div>
        <div v-else class="category-tree-list">
          <button
            v-for="group in categoryGroups"
            :key="group.id"
            type="button"
            class="category-tree-item"
            :class="{ active: filters.parentId === group.id }"
            @click="selectParent(group.id)"
          >
            <span class="category-tree-icon" :class="group.categoryType === 'EXPENSE' ? 'expense' : 'income'">
              <el-icon><component :is="iconComponent(group.icon)" /></el-icon>
            </span>
            <span class="category-tree-main">
              <strong>{{ group.categoryName }}</strong>
              <small>{{ group.children.length }} 个二级分类</small>
            </span>
            <el-tag :type="group.categoryType === 'EXPENSE' ? 'danger' : 'success'" effect="plain">
              {{ group.categoryType === 'EXPENSE' ? '支出' : '收入' }}
            </el-tag>
          </button>
        </div>
      </aside>

      <main class="category-table-panel">
        <div class="category-panel-head table-head">
          <div>
            <h3>分类列表</h3>
            <span>已选 {{ selectedIds.length }} 条，支持分页和批量操作</span>
          </div>
          <el-tag v-if="filters.parentId" effect="plain" closable @close="selectParent()">
            {{ parentNameMap[filters.parentId] }}
          </el-tag>
        </div>

        <el-table
          ref="tableRef"
          v-loading="loading.list"
          :data="pagedCategories"
          row-key="id"
          empty-text="暂无分类数据"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="48" />
          <el-table-column label="分类名称" min-width="250">
            <template #default="{ row }">
              <div class="category-name-cell" :class="{ child: Boolean(row.parentId) }">
                <span class="category-row-icon" :class="row.categoryType === 'EXPENSE' ? 'expense' : 'income'">
                  <el-icon><component :is="iconComponent(row.icon)" /></el-icon>
                </span>
                <div>
                  <strong>{{ row.categoryName }}</strong>
                  <small>{{ row.parentId ? parentNameMap[row.parentId] : '一级分类' }}</small>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="96">
            <template #default="{ row }">
              <el-tag :type="row.categoryType === 'EXPENSE' ? 'danger' : 'success'" effect="plain">
                {{ row.categoryType === 'EXPENSE' ? '支出' : '收入' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="层级" width="96">
            <template #default="{ row }">
              <span class="category-level-pill" :class="{ child: Boolean(row.parentId) }">
                {{ formatLevel(row.level) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="图标" min-width="150">
            <template #default="{ row }">
              <span class="category-icon-preview" :class="row.icon ? '' : 'manage-muted-dash'">
                <el-icon v-if="row.icon"><component :is="iconComponent(row.icon)" /></el-icon>
                {{ iconLabel(row.icon) || '未设置' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="排序" width="96">
            <template #default="{ row }">{{ formatSortOrder(row.sortOrder) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <div class="manage-action-links">
                <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
                <el-button link type="danger" @click="removeCategory(row)">删除</el-button>
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
            :total="filteredCategories.length"
            @current-change="clearSelection"
            @size-change="handlePageSizeChange"
          />
        </div>
      </main>
    </section>

    <el-dialog
      v-model="dialogVisible"
      class="category-dialog"
      :title="form.id ? '编辑分类' : '新增分类'"
      width="min(45rem, calc(100vw - 1.5rem))"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="category-form-grid">
          <el-form-item label="分类名称" prop="categoryName">
            <el-input v-model.trim="form.categoryName" maxlength="20" show-word-limit placeholder="例如：早餐、工资、交通" />
          </el-form-item>
          <el-form-item label="分类类型" prop="categoryType">
            <el-select v-model="form.categoryType" :disabled="Boolean(form.parentId)" style="width: 100%">
              <el-option label="支出" value="EXPENSE" />
              <el-option label="收入" value="INCOME" />
            </el-select>
          </el-form-item>
          <el-form-item label="父级分类" prop="parentId">
            <el-select v-model="form.parentId" clearable filterable style="width: 100%" placeholder="不选择则创建一级分类">
              <el-option
                v-for="item in parentCandidates"
                :key="item.id"
                :label="item.categoryName"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="层级">
            <el-input :model-value="form.parentId ? '二级分类' : '一级分类'" disabled />
          </el-form-item>
          <el-form-item label="排序" prop="sortOrder">
            <el-input-number v-model="form.sortOrder" :min="0" :max="999" style="width: 100%" />
          </el-form-item>
        </div>

        <el-form-item label="图标" prop="icon">
          <div class="category-icon-grid">
            <button
              v-for="item in visibleIconPresets"
              :key="item.value"
              type="button"
              :class="{ active: form.icon === item.value }"
              @click="form.icon = item.value"
            >
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.label }}</span>
            </button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading.saving" @click="saveCategory">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import {
  Basketball,
  Coin,
  Coffee,
  Delete,
  Document,
  FirstAidKit,
  Food,
  House,
  KnifeFork,
  Money,
  Phone,
  Plus,
  Present,
  Reading,
  Refresh,
  School,
  Search,
  ShoppingBag,
  SuitcaseLine,
  Tickets,
  Van,
  Wallet
} from '@element-plus/icons-vue'
import type { FormInstance, FormRules, TableInstance } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useBookContext } from '@/composables/useBookContext'
import request from '@/utils/request'

type CategoryType = 'EXPENSE' | 'INCOME'

interface CategoryItem {
  id: number
  bookId?: number
  parentId?: number | null
  categoryName: string
  categoryType: CategoryType
  icon?: string
  level?: number
  sortOrder?: number
  children?: CategoryItem[]
}

interface CategoryForm {
  id?: number
  bookId?: number
  parentId?: number
  categoryName: string
  categoryType: CategoryType
  icon: string
  level: number
  sortOrder: number
}

const categories = ref<CategoryItem[]>([])
const selectedIds = ref<number[]>([])
const dialogVisible = ref(false)
const tableRef = ref<TableInstance>()
const formRef = ref<FormInstance>()
const { currentBookId, loadBooks } = useBookContext()

const typeOptions = [
  { label: '全部', value: '' },
  { label: '支出', value: 'EXPENSE' },
  { label: '收入', value: 'INCOME' }
]

const iconPresets = [
  { value: 'food', label: '餐饮', type: 'EXPENSE', icon: KnifeFork },
  { value: 'coffee', label: '饮品', type: 'EXPENSE', icon: Coffee },
  { value: 'bus', label: '交通', type: 'EXPENSE', icon: Van },
  { value: 'home', label: '居家', type: 'EXPENSE', icon: House },
  { value: 'shopping-bag', label: '购物', type: 'EXPENSE', icon: ShoppingBag },
  { value: 'gamepad-2', label: '娱乐', type: 'EXPENSE', icon: Basketball },
  { value: 'gift', label: '人情', type: 'EXPENSE', icon: Present },
  { value: 'health', label: '医疗', type: 'EXPENSE', icon: FirstAidKit },
  { value: 'culture', label: '教育', type: 'EXPENSE', icon: Reading },
  { value: 'phone', label: '通讯', type: 'EXPENSE', icon: Phone },
  { value: 'school', label: '学费', type: 'EXPENSE', icon: School },
  { value: 'ticket', label: '票券', type: 'EXPENSE', icon: Tickets },
  { value: 'money', label: '工资', type: 'INCOME', icon: Money },
  { value: 'reimburse', label: '报销', type: 'INCOME', icon: Document },
  { value: 'subsidy', label: '补贴', type: 'INCOME', icon: Coin },
  { value: 'second-hand', label: '闲置', type: 'INCOME', icon: SuitcaseLine },
  { value: 'borrow', label: '借入', type: 'INCOME', icon: Wallet },
  { value: 'wallet', label: '钱包', type: 'INCOME', icon: Wallet },
  { value: 'coin', label: '理财', type: 'INCOME', icon: Coin },
  { value: 'other', label: '其他', type: 'BOTH', icon: Food }
]

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
  categoryType: '' as '' | CategoryType,
  keyword: '',
  parentId: undefined as number | undefined
})

const emptyForm = (): CategoryForm => ({
  id: undefined,
  bookId: currentBookId.value,
  parentId: undefined,
  categoryName: '',
  categoryType: (filters.categoryType || 'EXPENSE') as CategoryType,
  icon: defaultIcon((filters.categoryType || 'EXPENSE') as CategoryType),
  level: 1,
  sortOrder: nextSortOrder((filters.categoryType || 'EXPENSE') as CategoryType)
})

const form = reactive<CategoryForm>(emptyForm())

const rules: FormRules<CategoryForm> = {
  categoryName: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { min: 1, max: 20, message: '分类名称最多 20 个字', trigger: 'blur' },
    { validator: validateUniqueName, trigger: 'blur' }
  ],
  categoryType: [{ required: true, message: '请选择分类类型', trigger: 'change' }],
  icon: [{ required: true, message: '请选择分类图标', trigger: 'change' }],
  sortOrder: [{ required: true, message: '请设置排序', trigger: 'change' }]
}

const sortedCategories = computed(() => [...categories.value].sort(compareCategory))
const expenseCount = computed(() => categories.value.filter((item) => item.categoryType === 'EXPENSE').length)
const incomeCount = computed(() => categories.value.filter((item) => item.categoryType === 'INCOME').length)
const rootCount = computed(() => categories.value.filter((item) => !normalizeParentId(item.parentId)).length)
const childCount = computed(() => categories.value.length - rootCount.value)
const expenseRootCount = computed(() => categories.value.filter((item) => item.categoryType === 'EXPENSE' && !normalizeParentId(item.parentId)).length)
const incomeRootCount = computed(() => categories.value.filter((item) => item.categoryType === 'INCOME' && !normalizeParentId(item.parentId)).length)

const parentNameMap = computed<Record<number, string>>(() =>
  Object.fromEntries(categories.value.map((item) => [item.id, item.categoryName]))
)

const childrenMap = computed(() => {
  const map = new Map<number, CategoryItem[]>()
  sortedCategories.value.forEach((item) => {
    const parentId = normalizeParentId(item.parentId)
    if (!parentId) return
    const children = map.get(parentId) || []
    children.push(item)
    map.set(parentId, children)
  })
  return map
})

const filteredByTypeCategories = computed(() =>
  sortedCategories.value.filter((item) => !filters.categoryType || item.categoryType === filters.categoryType)
)

const categoryGroups = computed(() =>
  filteredByTypeCategories.value
    .filter((item) => !normalizeParentId(item.parentId))
    .map((item) => ({
      ...item,
      children: childrenMap.value.get(item.id) || []
    }))
)

const filteredCategories = computed(() =>
  filteredByTypeCategories.value.filter((item) => {
    const keyword = filters.keyword.trim().toLowerCase()
    const parentId = normalizeParentId(item.parentId)
    const matchParent = !filters.parentId || item.id === filters.parentId || parentId === filters.parentId
    const matchKeyword = !keyword
      || item.categoryName.toLowerCase().includes(keyword)
      || (parentId && parentNameMap.value[parentId]?.toLowerCase().includes(keyword))
    return matchParent && matchKeyword
  })
)

const pagedCategories = computed(() => {
  const start = (pageState.pageNum - 1) * pageState.pageSize
  return filteredCategories.value.slice(start, start + pageState.pageSize)
})

const parentCandidates = computed(() =>
  categories.value
    .filter((item) =>
      item.categoryType === form.categoryType
      && item.id !== form.id
      && !normalizeParentId(item.parentId)
      && Number(item.level || 1) === 1
    )
    .sort(compareCategory)
)

const visibleIconPresets = computed(() =>
  iconPresets.filter((item) => item.type === 'BOTH' || item.type === form.categoryType)
)

const activeFilterText = computed(() => {
  const parts = []
  if (filters.categoryType) parts.push(filters.categoryType === 'EXPENSE' ? '支出' : '收入')
  if (filters.parentId) parts.push(parentNameMap.value[filters.parentId])
  if (filters.keyword.trim()) parts.push(`"${filters.keyword.trim()}"`)
  return parts.length ? parts.join(' / ') : '全部分类'
})

function compareCategory(left: CategoryItem, right: CategoryItem) {
  const typeOrder = left.categoryType.localeCompare(right.categoryType)
  if (typeOrder !== 0) return typeOrder
  const leftParent = normalizeParentId(left.parentId) || left.id
  const rightParent = normalizeParentId(right.parentId) || right.id
  if (leftParent !== rightParent) return leftParent - rightParent
  const levelOrder = Number(left.level || 1) - Number(right.level || 1)
  if (levelOrder !== 0) return levelOrder
  return Number(left.sortOrder || 0) - Number(right.sortOrder || 0)
}

function normalizeParentId(value?: number | null) {
  return value && value > 0 ? value : undefined
}

function defaultIcon(type: CategoryType) {
  return type === 'INCOME' ? 'money' : 'food'
}

function nextSortOrder(type: CategoryType, parentId?: number) {
  return categories.value.filter((item) =>
    item.categoryType === type && normalizeParentId(item.parentId) === parentId
  ).length
}

function formatLevel(level?: number) {
  return Number(level || 1) === 2 ? '二级' : '一级'
}

function formatSortOrder(sortOrder?: number) {
  return typeof sortOrder === 'number' ? `第 ${sortOrder + 1} 位` : '-'
}

function iconComponent(value?: string) {
  const key = String(value || '').toLowerCase()
  const preset = iconPresets.find((item) => item.value === value)
  if (preset) return preset.icon
  if (key.includes('salary') || key.includes('工资') || key.includes('奖金') || key.includes('兼职')) return Money
  if (key.includes('red') || key.includes('红包') || key.includes('gift')) return Present
  if (key.includes('traffic') || key.includes('bus') || key.includes('car')) return Van
  if (key.includes('shop')) return ShoppingBag
  if (key.includes('health') || key.includes('medical')) return FirstAidKit
  if (key.includes('education') || key.includes('school')) return School
  if (key.includes('home') || key.includes('house')) return House
  if (key.includes('card') || key.includes('bank') || key.includes('wallet')) return Wallet
  if (key.includes('food') || key.includes('meal') || key.includes('dining')) return KnifeFork
  if (key.includes('coffee') || key.includes('drink')) return Coffee
  if (key.includes('sport') || key.includes('game')) return Basketball
  if (key.includes('coin') || key.includes('finance')) return Coin
  return Wallet
}

function iconLabel(value?: string) {
  const key = String(value || '').toLowerCase()
  const preset = iconPresets.find((item) => item.value === value)
  if (preset) return preset.label
  if (key.includes('salary') || key.includes('工资')) return '工资'
  if (key.includes('bonus') || key.includes('奖金')) return '奖金'
  if (key.includes('red') || key.includes('红包')) return '红包'
  if (key.includes('card') || key.includes('bank')) return '银行卡'
  if (key.includes('traffic') || key.includes('bus') || key.includes('car')) return '交通'
  if (key.includes('shop')) return '购物'
  if (key.includes('health') || key.includes('medical')) return '医疗'
  if (key.includes('education') || key.includes('school')) return '教育'
  if (key.includes('home') || key.includes('house')) return '居家'
  if (key.includes('food') || key.includes('meal') || key.includes('dining')) return '餐饮'
  if (key.includes('coffee') || key.includes('drink')) return '饮品'
  if (key.includes('coin') || key.includes('finance')) return '理财'
  return value
}

function validateUniqueName(_rule: unknown, value: string, callback: (error?: Error) => void) {
  const name = value?.trim()
  if (!name) {
    callback()
    return
  }
  const duplicated = categories.value.some((item) =>
    item.id !== form.id
    && item.categoryName === name
    && item.categoryType === form.categoryType
    && normalizeParentId(item.parentId) === form.parentId
  )
  callback(duplicated ? new Error('同一层级下已存在同名分类') : undefined)
}

function clearSelection() {
  selectedIds.value = []
  tableRef.value?.clearSelection()
}

function handleSelectionChange(selection: CategoryItem[]) {
  selectedIds.value = selection.map((item) => item.id)
}

function handlePageSizeChange() {
  pageState.pageNum = 1
  clearSelection()
}

function resetFilters() {
  filters.categoryType = ''
  filters.keyword = ''
  filters.parentId = undefined
  pageState.pageNum = 1
  clearSelection()
}

function selectParent(parentId?: number) {
  filters.parentId = parentId
  pageState.pageNum = 1
  clearSelection()
}

function resetForm(seed?: Partial<CategoryForm>) {
  Object.assign(form, emptyForm(), seed)
  syncFormDerivedFields()
}

async function openCreateDialog(parent?: CategoryItem) {
  const seed = parent
    ? {
        parentId: parent.id,
        categoryType: parent.categoryType,
        icon: parent.icon || defaultIcon(parent.categoryType),
        sortOrder: nextSortOrder(parent.categoryType, parent.id)
      }
    : undefined
  resetForm(seed)
  dialogVisible.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

async function openEditDialog(row: CategoryItem) {
  resetForm({
    ...row,
    parentId: normalizeParentId(row.parentId),
    icon: row.icon || defaultIcon(row.categoryType),
    level: Number(row.level || 1),
    sortOrder: Number(row.sortOrder || 0)
  })
  dialogVisible.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

function syncFormDerivedFields() {
  const parent = form.parentId ? categories.value.find((item) => item.id === form.parentId) : undefined
  if (parent) {
    form.categoryType = parent.categoryType
    form.level = 2
    if (!form.icon) form.icon = parent.icon || defaultIcon(parent.categoryType)
  } else {
    form.level = 1
    if (!form.icon || !visibleIconPresets.value.some((item) => item.value === form.icon)) {
      form.icon = defaultIcon(form.categoryType)
    }
  }
  form.bookId = currentBookId.value
}

async function loadCategories() {
  if (!currentBookId.value || loading.list) return
  loading.list = true
  try {
    const [expenseCategories, incomeCategories] = await Promise.all([
      request.get('/api/categories', { params: { bookId: currentBookId.value, categoryType: 'EXPENSE' } }),
      request.get('/api/categories', { params: { bookId: currentBookId.value, categoryType: 'INCOME' } })
    ])
    categories.value = [...expenseCategories, ...incomeCategories]
    if (filters.parentId && !categories.value.some((item) => item.id === filters.parentId)) {
      filters.parentId = undefined
    }
    if ((pageState.pageNum - 1) * pageState.pageSize >= filteredCategories.value.length && pageState.pageNum > 1) {
      pageState.pageNum = Math.max(1, pageState.pageNum - 1)
    }
    clearSelection()
  } finally {
    loading.list = false
  }
}

async function saveCategory() {
  if (loading.saving) return
  syncFormDerivedFields()
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.saving = true
  try {
    const payload = {
      ...form,
      categoryName: form.categoryName.trim(),
      parentId: form.parentId || null
    }
    if (form.id) {
      await request.put(`/api/categories/${form.id}`, payload)
    } else {
      await request.post('/api/categories', payload)
    }
    ElMessage.success(form.id ? '分类已更新' : '分类已新增')
    dialogVisible.value = false
    await loadCategories()
  } finally {
    loading.saving = false
  }
}

async function removeCategory(row: CategoryItem) {
  if (loading.deleting) return
  const childTotal = childrenMap.value.get(row.id)?.length || 0
  const message = childTotal
    ? `该分类下还有 ${childTotal} 个二级分类，删除后不可恢复，是否继续？`
    : '删除后不可恢复，是否继续？'
  await ElMessageBox.confirm(message, '删除分类', { type: 'warning' })
  loading.deleting = true
  try {
    await request.delete(`/api/categories/${row.id}`)
    ElMessage.success('分类已删除')
    await loadCategories()
  } finally {
    loading.deleting = false
  }
}

async function removeSelectedCategories() {
  if (loading.deleting || !selectedIds.value.length) return
  await ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 个分类吗？`, '批量删除分类', {
    type: 'warning'
  })
  loading.deleting = true
  try {
    await request.delete('/api/categories/batch', { data: { ids: selectedIds.value } })
    ElMessage.success('选中分类已删除')
    await loadCategories()
  } finally {
    loading.deleting = false
  }
}

watch(
  () => form.parentId,
  (value, oldValue) => {
    syncFormDerivedFields()
    if (!form.id && value !== oldValue) {
      form.sortOrder = nextSortOrder(form.categoryType, value)
    }
  }
)

watch(
  () => form.categoryType,
  (value, oldValue) => {
    if (value !== oldValue && form.parentId && !parentCandidates.value.some((item) => item.id === form.parentId)) {
      form.parentId = undefined
    }
    if (!visibleIconPresets.value.some((item) => item.value === form.icon)) {
      form.icon = defaultIcon(value)
    }
    if (!form.id && value !== oldValue) {
      form.sortOrder = nextSortOrder(value, form.parentId)
    }
  }
)

watch(
  () => [filters.categoryType, filters.keyword],
  () => {
    pageState.pageNum = 1
    if (filters.parentId && !categoryGroups.value.some((item) => item.id === filters.parentId)) {
      filters.parentId = undefined
    }
    clearSelection()
  }
)

watch(currentBookId, async (value, oldValue) => {
  if (!value || value === oldValue) return
  pageState.pageNum = 1
  await loadCategories()
  resetForm()
}, { immediate: false })

onMounted(async () => {
  await loadBooks()
  await loadCategories()
})
</script>

<style scoped lang="stylus">
.category-manage-shell
  display flex
  flex-direction column
  gap 1rem

.category-page-head,
.category-control-card,
.category-tree-panel,
.category-table-panel,
.category-stat-card
  background #fff
  border 0.0625rem solid rgba(64, 141, 134, 0.1)
  border-radius 0.5rem
  box-shadow 0 0.625rem 1.5rem rgba(29, 50, 61, 0.045)

.category-page-head
  display flex
  justify-content space-between
  align-items center
  gap 1rem
  padding 1.125rem 1.25rem
  background linear-gradient(135deg, #ffffff 0%, #f2fbf9 55%, #fff6f5 100%)

.category-page-head h2,
.category-panel-head h3
  margin 0
  color #132933

.category-page-head h2
  font-size 1.625rem
  font-weight 900

.category-page-head p,
.category-panel-head span,
.category-stat-card span,
.category-stat-card small,
.category-name-cell small,
.category-tree-main small
  color #728692
  font-size 0.8125rem

.category-page-head p
  margin 0.5rem 0 0
  max-width 42rem
  line-height 1.65

.manage-section-eyebrow
  display inline-flex
  align-items center
  min-height 1.5rem
  padding 0 0.625rem
  border-radius 62.4375rem
  background #e9f8f5
  color #168a78
  font-weight 800

.category-stat-grid
  display grid
  grid-template-columns repeat(4, minmax(0, 1fr))
  gap 0.75rem

.category-stat-card
  min-height 6.25rem
  padding 1rem
  display grid
  align-content center
  gap 0.375rem
  position relative
  overflow hidden

.category-stat-card::after
  content ''
  position absolute
  left 1rem
  right 1rem
  bottom 0
  height 0.1875rem
  border-radius 62.4375rem
  background #d8e4ea

.category-stat-card.expense::after
  background linear-gradient(90deg, #ff6b6f, #ffb7b9)

.category-stat-card.income::after
  background linear-gradient(90deg, #26a69a, #43c66a)

.category-stat-card.selected::after
  background linear-gradient(90deg, #4f8ef7, #26a69a)

.category-stat-card strong
  font-size 1.625rem
  line-height 1.05
  color #132933
  font-weight 900
  font-variant-numeric tabular-nums

.category-control-card
  padding 1rem

.category-filter-row,
.category-control-actions,
.category-panel-head,
.category-tree-item,
.category-name-cell,
.category-icon-preview
  display flex
  align-items center

.category-filter-row
  gap 0.75rem
  flex-wrap wrap

.category-search
  width min(22.5rem, 100%)

.category-type-tabs
  flex 0 0 auto

.category-control-actions
  gap 0.625rem
  margin-left auto

.category-workbench
  display grid
  grid-template-columns 21rem minmax(0, 1fr)
  gap 1rem
  align-items start

.category-tree-panel,
.category-table-panel
  min-width 0

.category-tree-panel
  padding 1rem
  position sticky
  top 5.125rem

.category-panel-head
  justify-content space-between
  gap 0.75rem
  margin-bottom 0.875rem

.category-panel-head h3
  font-size 1rem
  font-weight 900

.category-tree-all,
.category-tree-item
  width 100%
  border 0.0625rem solid #edf3f2
  background linear-gradient(180deg, #ffffff 0%, #fbfefd 100%)
  color #132933
  border-radius 0.5rem
  cursor pointer
  transition border-color 0.18s ease, background 0.18s ease, transform 0.18s ease

.category-tree-all
  height 2.75rem
  display flex
  justify-content space-between
  align-items center
  padding 0 0.75rem
  margin-bottom 0.625rem
  font-weight 800

.category-tree-all.active,
.category-tree-item.active,
.category-tree-all:hover,
.category-tree-item:hover
  border-color #9bd8d1
  background #f3fbf9
  transform translateY(-0.0625rem)

.category-tree-item.active
  box-shadow 0 0 0 0.1875rem rgba(38, 166, 154, 0.09)

.category-tree-list
  display grid
  gap 0.5rem
  max-height calc(100vh - 22.5rem)
  overflow auto
  scrollbar-width none

.category-tree-list::-webkit-scrollbar
  display none

.category-tree-item
  gap 0.75rem
  padding 0.75rem
  text-align left

.category-tree-icon,
.category-row-icon
  display inline-grid
  place-items center
  flex 0 0 auto

.category-tree-icon
  width 2.5rem
  height 2.5rem
  border-radius 0.5rem

.category-row-icon
  width 2.25rem
  height 2.25rem
  border-radius 0.5rem

.category-tree-icon.expense,
.category-row-icon.expense
  color #ff4d4f
  background #fff0f0

.category-tree-icon.income,
.category-row-icon.income
  color #15966f
  background #edf9f5

.category-tree-main
  min-width 0
  flex 1
  display grid
  gap 0.1875rem

.category-tree-main strong,
.category-name-cell strong
  overflow hidden
  text-overflow ellipsis
  white-space nowrap
  color #132933

.category-table-panel
  overflow hidden

.category-table-panel .table-head
  padding 1rem 1.125rem
  margin 0
  background linear-gradient(180deg, #fbfefd 0%, #ffffff 100%)
  border-bottom 0.0625rem solid #edf3f2

.category-table-panel :deep(.el-table)
  --el-table-border-color #edf3f2
  --el-table-header-bg-color #f5faf9
  --el-table-row-hover-bg-color #f3fbf9

.category-table-panel :deep(.el-table th.el-table__cell)
  color #5f7580
  font-weight 800

.category-table-panel :deep(.el-table td.el-table__cell),
.category-table-panel :deep(.el-table th.el-table__cell)
  padding-top 0.875rem
  padding-bottom 0.875rem

.category-table-panel .manage-pagination
  margin 0 1.125rem
  padding 0.875rem 0 1rem

.category-name-cell
  gap 0.75rem
  min-width 0

.category-name-cell.child
  padding-left 1.125rem

.category-name-cell > div
  min-width 0
  display grid
  gap 0.25rem

.category-level-pill
  display inline-flex
  align-items center
  justify-content center
  min-width 2.875rem
  height 1.5rem
  padding 0 0.625rem
  border-radius 62.4375rem
  background #eef3f5
  color #5f7480
  font-size 0.75rem
  font-weight 800

.category-level-pill.child
  background #eef7ff
  color #237fd1

.category-form-grid
  display grid
  grid-template-columns repeat(2, minmax(0, 1fr))
  gap 0 0.875rem

.category-dialog :deep(.el-dialog__body)
  padding-top 0.5rem

.category-tree-empty
  padding 1.25rem 0

@media (max-width: 73.75rem)
  .category-stat-grid,
  .category-workbench
    grid-template-columns 1fr

  .category-tree-panel
    position static

  .category-tree-list
    max-height 20rem

@media (max-width: 47.5rem)
  .category-page-head,
  .category-filter-row,
  .category-control-actions
    align-items stretch
    flex-direction column

  .category-page-head .el-button,
  .category-control-actions,
  .category-control-actions .el-button,
  .category-search
    width 100%

  .category-form-grid
    grid-template-columns 1fr
</style>
