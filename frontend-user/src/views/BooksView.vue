<template>
  <div class="manage-shell">
    <div class="manage-hero">
      <div>
        <div class="manage-section-eyebrow">Books</div>
        <h2>账本管理</h2>
        <p>维护个人、家庭、项目等不同账本，并设置默认账本。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreateDialog">新增账本</el-button>
    </div>

    <div class="book-grid">
      <article v-for="book in books" :key="book.id" class="book-card" :class="{ active: currentBookId === book.id }">
        <div class="book-card-icon">
          <el-icon><Files /></el-icon>
        </div>
        <div class="book-card-main">
          <div class="book-card-title">
            {{ book.bookName }}
            <el-tag v-if="book.isDefault === 1" size="small" type="success">默认</el-tag>
          </div>
          <div class="book-card-meta">{{ book.currencyCode || 'CNY' }} · {{ currentBookId === book.id ? '当前账本' : '可切换账本' }}</div>
        </div>
        <div class="book-card-actions">
          <el-button text type="primary" @click="setCurrentBookId(book.id)">切换</el-button>
          <el-button text @click="openEditDialog(book)">编辑</el-button>
          <el-button text type="danger" @click="removeBook(book.id)">删除</el-button>
        </div>
      </article>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑账本' : '新增账本'" width="460px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="账本名称" prop="bookName">
          <el-input v-model="form.bookName" placeholder="例如：个人生活账本、家庭共用账本" />
        </el-form-item>
        <el-form-item label="币种" prop="currencyCode">
          <el-select v-model="form.currencyCode" style="width:100%">
            <el-option label="人民币 CNY" value="CNY" />
            <el-option label="美元 USD" value="USD" />
            <el-option label="欧元 EUR" value="EUR" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="isDefault">设为默认账本</el-checkbox>
        </el-form-item>
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
import { Files, Plus } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useBookContext } from '@/composables/useBookContext'
import request from '@/utils/request'

const { books, currentBookId, loadBooks, setCurrentBookId } = useBookContext()
const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const isDefault = ref(false)
const form = reactive({
  id: undefined as number | undefined,
  bookName: '',
  currencyCode: 'CNY'
})

const rules: FormRules = {
  bookName: [{ required: true, message: '请输入账本名称', trigger: 'blur' }],
  currencyCode: [{ required: true, message: '请选择币种', trigger: 'change' }]
}

function resetForm() {
  Object.assign(form, { id: undefined, bookName: '', currencyCode: 'CNY' })
  isDefault.value = false
}

function openCreateDialog() {
  resetForm()
  dialogVisible.value = true
}

function openEditDialog(book: any) {
  Object.assign(form, {
    id: book.id,
    bookName: book.bookName,
    currencyCode: book.currencyCode || 'CNY'
  })
  isDefault.value = book.isDefault === 1
  dialogVisible.value = true
}

async function saveBook() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await request.post('/api/books', { ...form, isDefault: isDefault.value ? 1 : 0 })
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

onMounted(() => loadBooks(true))
</script>
