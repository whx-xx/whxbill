<template>
  <div class="books-shell">
    <section class="books-hero">
      <div class="books-hero-copy">
        <div class="manage-section-eyebrow">Books</div>
        <h2>账本管理</h2>
        <p>维护个人、家庭、项目等不同账本，快速切换当前账本，并设置默认使用的账本。</p>
      </div>

      <div class="books-hero-side">
        <div class="books-summary">
          <div>
            <span>账本总数</span>
            <strong>{{ books.length }}</strong>
          </div>
          <div>
            <span>当前账本</span>
            <strong>{{ currentBook?.bookName || '未选择' }}</strong>
          </div>
        </div>
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">新增账本</el-button>
      </div>
    </section>

    <section v-if="books.length" class="book-wallet-grid">
      <article
        v-for="book in books"
        :key="book.id"
        class="book-wallet-card"
        :class="{ active: isCurrentBook(book), default: book.isDefault === 1 }"
      >
        <div class="book-wallet-glow"></div>
        <div class="book-wallet-head">
          <div class="book-wallet-icon">
            <el-icon><Wallet /></el-icon>
          </div>
          <div class="book-wallet-tags">
            <el-tag v-if="isCurrentBook(book)" type="success" effect="dark">当前使用中</el-tag>
            <el-tag v-if="book.isDefault === 1" effect="plain">默认账本</el-tag>
          </div>
        </div>

        <div class="book-wallet-body">
          <span>{{ currencyName(book.currencyCode) }}</span>
          <h3>{{ book.bookName }}</h3>
          <p>{{ isCurrentBook(book) ? '正在为全站账单、预算和统计提供数据上下文' : '可切换为当前账本继续管理收支数据' }}</p>
        </div>

        <div class="book-wallet-meta">
          <div>
            <span>币种</span>
            <strong>{{ currencyDisplay(book.currencyCode) }}</strong>
          </div>
          <div>
            <span>状态</span>
            <strong>{{ isCurrentBook(book) ? '使用中' : '可切换' }}</strong>
          </div>
        </div>

        <div class="book-wallet-actions">
          <el-button v-if="isCurrentBook(book)" disabled :icon="Check">使用中</el-button>
          <el-button v-else class="book-switch-button" type="primary" :icon="Switch" @click="setCurrentBookId(book.id)">切换</el-button>
          <el-button plain @click="openEditDialog(book)">编辑</el-button>
          <el-button plain type="danger" @click="removeBook(book.id)">删除</el-button>
        </div>
      </article>
    </section>

    <section v-else class="book-empty-card">
      <div class="book-empty-icon">
        <el-icon><Files /></el-icon>
      </div>
      <h3>还没有账本</h3>
      <p>先创建一个个人或家庭账本，之后账单、预算、统计都会围绕它展开。</p>
      <el-button type="primary" :icon="Plus" @click="openCreateDialog">新建第一个账本</el-button>
    </section>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑账本' : '新增账本'" width="min(31.25rem, calc(100vw - 1.5rem))">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="book-dialog-card">
          <div class="book-dialog-preview">
            <div class="book-dialog-mark">
              <el-icon><Wallet /></el-icon>
            </div>
            <div>
              <strong>{{ form.bookName || '新的账本' }}</strong>
              <span>{{ currencyDisplay(form.currencyCode) }} · {{ isDefault ? '将设为默认账本' : '普通账本' }}</span>
            </div>
          </div>

          <el-form-item label="账本名称" prop="bookName">
            <el-input v-model="form.bookName" placeholder="例如：个人生活账本、家庭共用账本" />
          </el-form-item>
          <el-form-item label="币种" prop="currencyCode">
            <el-select v-model="form.currencyCode" :disabled="currencyLocked" style="width:100%">
              <el-option
                v-for="item in currencyOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <div v-if="currencyLocked" class="book-form-tip">该账本下已有账单，为避免历史金额单位混乱，币种不可修改。</div>
          </el-form-item>
          <el-form-item class="book-default-item">
            <el-checkbox v-model="isDefault">设为默认账本</el-checkbox>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveBook">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Check, Files, Plus, Switch, Wallet } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useBookContext } from '@/composables/useBookContext'
import request from '@/utils/request'

const { books, currentBookId, loadBooks, setCurrentBookId } = useBookContext()
const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const isDefault = ref(false)
const editingBillCount = ref(0)
const currencyDicts = ref<any[]>([])
const form = reactive({
  id: undefined as number | undefined,
  bookName: '',
  currencyCode: 'CNY'
})
const currentBook = computed(() => books.value.find((book) => book.id === currentBookId.value))
const currencyLocked = computed(() => Boolean(form.id && editingBillCount.value > 0))
const fallbackCurrencies = [
  { dictLabel: '人民币', dictValue: 'CNY', dictExtra: '{"symbol":"¥","locale":"zh-CN","fractionDigits":2}' },
  { dictLabel: '美元', dictValue: 'USD', dictExtra: '{"symbol":"$","locale":"en-US","fractionDigits":2}' },
  { dictLabel: '欧元', dictValue: 'EUR', dictExtra: '{"symbol":"€","locale":"de-DE","fractionDigits":2}' }
]
const currencyOptions = computed(() => (currencyDicts.value.length ? currencyDicts.value : fallbackCurrencies).map((item) => {
  const extra = currencyExtra(item)
  return {
    value: item.dictValue,
    label: `${item.dictLabel} ${item.dictValue}${extra.symbol ? ` ${extra.symbol}` : ''}`
  }
}))

const rules: FormRules = {
  bookName: [{ required: true, message: '请输入账本名称', trigger: 'blur' }],
  currencyCode: [{ required: true, message: '请选择币种', trigger: 'change' }]
}

function isCurrentBook(book: any) {
  return book.id === currentBookId.value
}

function currencyName(value?: string) {
  const item = currencyDict(value)
  return item ? `${item.dictLabel}账本` : `${value || 'CNY'} 账本`
}

function currencyDisplay(value?: string) {
  const item = currencyDict(value)
  const extra = currencyExtra(item)
  const code = item?.dictValue || value || 'CNY'
  return extra.symbol ? `${code} ${extra.symbol}` : code
}

function currencyDict(value?: string) {
  const code = String(value || 'CNY').toUpperCase()
  return (currencyDicts.value.length ? currencyDicts.value : fallbackCurrencies)
    .find((item) => String(item.dictValue || '').toUpperCase() === code)
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

function resetForm() {
  Object.assign(form, { id: undefined, bookName: '', currencyCode: 'CNY' })
  isDefault.value = false
  editingBillCount.value = 0
}

function openCreateDialog() {
  resetForm()
  dialogVisible.value = true
}

async function openEditDialog(book: any) {
  Object.assign(form, {
    id: book.id,
    bookName: book.bookName,
    currencyCode: book.currencyCode || 'CNY'
  })
  isDefault.value = book.isDefault === 1
  editingBillCount.value = await loadBookBillCount(book.id)
  dialogVisible.value = true
}

async function loadBookBillCount(bookId: number) {
  const result = await request.get('/api/bills', {
    params: { bookId, pageNum: 1, pageSize: 1 }
  })
  return Number(result?.total || 0)
}

async function saveBook() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = { ...form, isDefault: isDefault.value ? 1 : 0 }
    // 创建和修改拆分为 POST/PUT，和后端 RESTful 接口保持一致。
    if (form.id) await request.put(`/api/books/${form.id}`, payload)
    else await request.post('/api/books', payload)
    ElMessage.success(form.id ? '账本已更新' : '账本已新增')
    dialogVisible.value = false
    await loadBooks(true)
  } finally {
    saving.value = false
  }
}

async function removeBook(bookId: number) {
  await ElMessageBox.confirm('删除账本前请确认没有关联账单，是否继续？', '删除账本', { type: 'warning' })
  await request.delete(`/api/books/${bookId}`)
  ElMessage.success('账本已删除')
  if (currentBookId.value === bookId) {
    setCurrentBookId(undefined)
  }
  await loadBooks(true)
}

onMounted(async () => {
  await Promise.all([loadBooks(true), loadCurrencyDicts()])
})
</script>

<style scoped lang="stylus">
.books-shell
  display grid
  gap 1rem

.books-hero
  display flex
  align-items stretch
  justify-content space-between
  gap 1rem
  padding 1.125rem 1.25rem
  border 0.0625rem solid rgba(64, 141, 134, 0.12)
  border-radius 0.75rem
  background linear-gradient(135deg, #ffffff 0%, #f2fbf9 58%, #eef7ff 100%)
  box-shadow 0 0.75rem 1.75rem rgba(29, 50, 61, 0.055)

.books-hero-copy
  min-width 0

.books-hero h2
  margin 0
  color #132933
  font-size 1.625rem
  font-weight 900
  line-height 1.2

.books-hero p
  margin 0.5rem 0 0
  max-width 42rem
  color #667985
  font-size 0.8125rem
  line-height 1.65

.books-hero-side
  display flex
  align-items center
  gap 0.875rem
  flex-wrap wrap
  justify-content flex-end

.books-summary
  display grid
  grid-template-columns repeat(2, minmax(6rem, auto))
  gap 0.625rem

.books-summary > div
  min-height 3.875rem
  display grid
  align-content center
  gap 0.375rem
  padding 0.75rem 0.875rem
  border 0.0625rem solid rgba(64, 141, 134, 0.12)
  border-radius 0.75rem
  background rgba(255, 255, 255, 0.78)

.books-summary span,
.book-wallet-body span,
.book-wallet-meta span,
.book-dialog-preview span
  color #728692
  font-size 0.75rem
  font-weight 700

.books-summary strong
  max-width 11rem
  color #132933
  font-size 1.125rem
  font-weight 900
  overflow hidden
  text-overflow ellipsis
  white-space nowrap

.book-wallet-grid
  display grid
  grid-template-columns repeat(auto-fit, minmax(18.75rem, 1fr))
  gap 1rem

.book-wallet-card
  position relative
  min-height 19rem
  display grid
  gap 1rem
  overflow hidden
  padding 1.125rem
  border 0.0625rem solid rgba(64, 141, 134, 0.12)
  border-radius 1rem
  background linear-gradient(145deg, #ffffff 0%, #f7fcfb 48%, #edf8ff 100%)
  box-shadow 0 0.75rem 1.75rem rgba(29, 50, 61, 0.055)
  transition border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease

.book-wallet-card:hover
  border-color #9bd8d1
  box-shadow 0 1rem 2.25rem rgba(29, 50, 61, 0.075)
  transform translateY(-0.0625rem)

.book-wallet-card.active
  border-color #26a69a
  background linear-gradient(145deg, #f5fffd 0%, #eaf8f5 48%, #eef7ff 100%)
  box-shadow 0 0 0 0.1875rem rgba(38, 166, 154, 0.1), 0 1rem 2.25rem rgba(38, 166, 154, 0.12)

.book-wallet-glow
  position absolute
  width 10rem
  height 10rem
  right -3.5rem
  top -3.5rem
  border-radius 50%
  background radial-gradient(circle, rgba(38, 166, 154, 0.2), transparent 68%)
  pointer-events none

.book-wallet-card.default .book-wallet-glow
  background radial-gradient(circle, rgba(79, 142, 247, 0.18), transparent 68%)

.book-wallet-head
  position relative
  z-index 1
  display flex
  align-items flex-start
  justify-content space-between
  gap 0.75rem

.book-wallet-icon,
.book-empty-icon,
.book-dialog-mark
  display grid
  place-items center
  background linear-gradient(135deg, #26a69a 0%, #408d86 100%)
  color #fff
  box-shadow 0 0.75rem 1.5rem rgba(38, 166, 154, 0.18)

.book-wallet-icon
  width 3rem
  height 3rem
  border-radius 0.875rem
  font-size 1.375rem

.book-wallet-tags
  display flex
  gap 0.375rem
  flex-wrap wrap
  justify-content flex-end

.book-wallet-body
  position relative
  z-index 1
  display grid
  gap 0.5rem

.book-wallet-body h3
  margin 0
  min-height 2.75rem
  color #132933
  font-size 1.5rem
  font-weight 900
  line-height 1.18
  overflow-wrap anywhere

.book-wallet-body p
  margin 0
  color #667985
  font-size 0.8125rem
  line-height 1.6

.book-wallet-meta
  position relative
  z-index 1
  display grid
  grid-template-columns repeat(2, minmax(0, 1fr))
  gap 0.625rem

.book-wallet-meta > div
  display grid
  gap 0.3125rem
  padding 0.75rem
  border 0.0625rem solid rgba(64, 141, 134, 0.1)
  border-radius 0.75rem
  background rgba(255, 255, 255, 0.72)

.book-wallet-meta strong
  color #132933
  font-size 0.9375rem
  font-weight 900

.book-wallet-actions
  position relative
  z-index 1
  display flex
  align-items center
  gap 0.5rem
  flex-wrap wrap
  padding-top 0.875rem
  border-top 0.0625rem solid rgba(64, 141, 134, 0.1)

.book-wallet-actions .el-button
  margin-left 0

.book-wallet-actions .book-switch-button
  color #fff
  background #0f766e
  border-color #0f766e

.book-wallet-actions .book-switch-button :deep(span),
.book-wallet-actions .book-switch-button :deep(.el-icon)
  color #fff

.book-wallet-actions .book-switch-button:hover,
.book-wallet-actions .book-switch-button:focus
  color #fff
  background #0a645d
  border-color #0a645d

.book-empty-card
  min-height 24rem
  display grid
  place-items center
  align-content center
  gap 0.75rem
  padding 2rem
  text-align center
  border 0.0625rem solid rgba(64, 141, 134, 0.12)
  border-radius 1rem
  background linear-gradient(145deg, #ffffff 0%, #f7fcfb 52%, #eef7ff 100%)
  box-shadow 0 0.75rem 1.75rem rgba(29, 50, 61, 0.055)

.book-empty-icon
  width 4rem
  height 4rem
  border-radius 1.125rem
  font-size 1.75rem

.book-empty-card h3
  margin 0.25rem 0 0
  color #132933
  font-size 1.25rem
  font-weight 900

.book-empty-card p
  margin 0
  max-width 24rem
  color #667985
  font-size 0.875rem
  line-height 1.7

.book-dialog-card
  display grid
  gap 1rem

.book-dialog-preview
  display grid
  grid-template-columns 3rem minmax(0, 1fr)
  align-items center
  gap 0.75rem
  padding 0.875rem
  border 0.0625rem solid rgba(64, 141, 134, 0.12)
  border-radius 0.875rem
  background linear-gradient(135deg, #f8fcfb 0%, #eef8ff 100%)

.book-dialog-mark
  width 3rem
  height 3rem
  border-radius 0.875rem
  font-size 1.25rem

.book-dialog-preview strong
  display block
  min-width 0
  color #132933
  font-size 1rem
  font-weight 900
  overflow hidden
  text-overflow ellipsis
  white-space nowrap

.book-dialog-preview span
  display block
  margin-top 0.25rem

.book-form-tip
  margin-top 0.375rem
  color #6f8290
  font-size 0.75rem
  line-height 1.6

.book-default-item
  margin-bottom 0

@media (max-width: 52rem)
  .books-hero
    flex-direction column

  .books-hero-side
    justify-content stretch

  .books-summary
    width 100%
    grid-template-columns 1fr

  .books-hero-side .el-button
    width 100%

  .book-wallet-grid
    grid-template-columns 1fr

  .book-wallet-actions .el-button
    flex 1 1 7rem
</style>
