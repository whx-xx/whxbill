<template>
  <div class="notes-shell">
    <section class="notes-editor-card">
      <div class="notes-card-head">
        <div>
          <span class="notes-eyebrow">Personal Notes</span>
          <h2>我的笔记</h2>
          <p>记录预算复盘、消费提醒和理财想法，数据保存在当前浏览器本地。</p>
        </div>
        <div class="notes-actions">
          <el-button @click="resetDraft">清空</el-button>
          <el-button type="primary" :loading="saving" @click="saveNote">保存笔记</el-button>
        </div>
      </div>

      <div class="notes-summary-grid">
        <div>
          <span>笔记数量</span>
          <strong>{{ notes.length }}</strong>
        </div>
        <div>
          <span>当前字数</span>
          <strong>{{ draftTextLength }}</strong>
        </div>
      </div>

      <el-form ref="formRef" :model="draft" :rules="rules" label-position="top">
        <el-form-item label="标题" prop="title">
          <el-input v-model="draft.title" maxlength="40" show-word-limit placeholder="例如：六月预算复盘" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <div class="notes-editor-wrap">
            <div ref="editorRef" class="notes-editor"></div>
          </div>
        </el-form-item>
      </el-form>
    </section>

    <aside class="notes-list-card">
      <div class="notes-list-head">
        <div>
          <h3>笔记列表</h3>
          <span>{{ notes.length }} 条</span>
        </div>
      </div>
      <el-empty v-if="notes.length === 0" description="暂无笔记" />
      <div v-else class="notes-scroll-list">
        <article v-for="note in notes" :key="note.id" class="notes-item" @click="loadNote(note)">
          <div class="notes-item-title">
            <h4>{{ note.title }}</h4>
            <el-button :icon="Delete" text type="danger" @click.stop="deleteNote(note.id)" />
          </div>
          <small>{{ note.updatedAt }}</small>
          <div class="notes-rich-text" v-html="sanitizeHtml(note.content)"></div>
        </article>
      </div>
    </aside>
  </div>
</template>

<script setup lang="ts">
import Quill from 'quill'
import 'quill/dist/quill.snow.css'
import { Delete } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, onMounted, reactive, ref } from 'vue'

type NoteDraft = {
  id?: number
  title: string
  content: string
}

type SavedNote = Required<NoteDraft> & {
  updatedAt: string
}

const storageKey = 'whx-bill-user-notes'
const editorRef = ref<HTMLDivElement>()
const formRef = ref<FormInstance>()
const quillRef = ref<Quill>()
const notes = ref<SavedNote[]>([])
const saving = ref(false)
const draft = reactive<NoteDraft>({
  title: '本月预算复盘',
  content: '<p>记录今天的理财感受、预算调整或阅读到的消费提醒。</p>'
})

const draftTextLength = computed(() => getPlainText(draft.content).length)
const rules: FormRules = {
  title: [
    { required: true, message: '请输入笔记标题', trigger: 'blur' },
    { min: 2, max: 40, message: '标题长度为 2-40 个字符', trigger: 'blur' }
  ],
  content: [{ required: true, message: '请输入笔记内容', trigger: 'change' }]
}

onMounted(async () => {
  loadLocalNotes()
  await nextTick()
  if (editorRef.value) {
    quillRef.value = new Quill(editorRef.value, {
      theme: 'snow',
      placeholder: '记录预算调整、消费提醒或复盘结论',
      modules: {
        toolbar: [
          ['bold', 'italic', 'underline'],
          [{ header: [2, 3, false] }],
          [{ list: 'ordered' }, { list: 'bullet' }],
          ['link', 'clean']
        ]
      }
    })
    quillRef.value.root.innerHTML = draft.content
    quillRef.value.on('text-change', () => {
      draft.content = quillRef.value?.root.innerHTML || ''
      formRef.value?.validateField('content').catch(() => undefined)
    })
  }
})

function loadLocalNotes() {
  try {
    notes.value = JSON.parse(localStorage.getItem(storageKey) || '[]')
  } catch {
    notes.value = []
  }
}

function persistNotes() {
  localStorage.setItem(storageKey, JSON.stringify(notes.value))
}

function getPlainText(html: string) {
  const container = document.createElement('div')
  container.innerHTML = sanitizeHtml(html)
  return container.textContent?.trim() || ''
}

function resetDraft() {
  draft.id = undefined
  draft.title = ''
  draft.content = ''
  quillRef.value?.setText('')
  formRef.value?.clearValidate()
}

function loadNote(note: SavedNote) {
  draft.id = note.id
  draft.title = note.title
  draft.content = note.content
  if (quillRef.value) {
    quillRef.value.root.innerHTML = sanitizeHtml(note.content)
  }
}

async function saveNote() {
  if (saving.value) return
  draft.content = sanitizeHtml(quillRef.value?.root.innerHTML || draft.content)
  if (!getPlainText(draft.content)) {
    ElMessage.warning('请输入笔记内容')
    return
  }
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const now = new Date().toLocaleString('zh-CN', { hour12: false })
    if (draft.id) {
      notes.value = notes.value.map((item) =>
        item.id === draft.id ? { ...item, title: draft.title.trim(), content: draft.content, updatedAt: now } : item
      )
    } else {
      notes.value.unshift({
        id: Date.now(),
        title: draft.title.trim(),
        content: draft.content,
        updatedAt: now
      })
    }
    persistNotes()
    ElMessage.success('笔记已保存')
    resetDraft()
  } finally {
    saving.value = false
  }
}

async function deleteNote(noteId: number) {
  await ElMessageBox.confirm('删除后不可恢复，是否继续？', '删除笔记', { type: 'warning' })
  notes.value = notes.value.filter((item) => item.id !== noteId)
  persistNotes()
  if (draft.id === noteId) resetDraft()
  ElMessage.success('笔记已删除')
}

function sanitizeHtml(html?: string) {
  const template = document.createElement('template')
  template.innerHTML = html || ''
  const allowedTags = new Set(['P', 'BR', 'STRONG', 'B', 'EM', 'I', 'U', 'UL', 'OL', 'LI', 'A', 'H2', 'H3', 'BLOCKQUOTE'])
  const allowedAttrs = new Map([['A', new Set(['href', 'target', 'rel'])]])

  template.content.querySelectorAll('*').forEach((element) => {
    if (!allowedTags.has(element.tagName)) {
      element.replaceWith(...Array.from(element.childNodes))
      return
    }
    Array.from(element.attributes).forEach((attr) => {
      const attrs = allowedAttrs.get(element.tagName)
      const value = attr.value.trim().toLowerCase()
      if (!attrs?.has(attr.name) || value.startsWith('javascript:')) {
        element.removeAttribute(attr.name)
      }
    })
    if (element.tagName === 'A') {
      element.setAttribute('target', '_blank')
      element.setAttribute('rel', 'noopener noreferrer')
    }
  })
  return template.innerHTML
}
</script>

<style scoped lang="stylus">
.notes-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(24rem, 30rem);
  gap: 0.75rem;
  min-height: calc(100vh - 5.75rem);
}

.notes-editor-card,
.notes-list-card {
  min-width: 0;
  padding: 1rem;
  background: #fff;
  border: 0.0625rem solid #e3ecef;
  border-radius: 0.75rem;
  box-shadow: 0 0.75rem 1.75rem rgba(23, 37, 45, 0.05);
}

.notes-card-head,
.notes-list-head,
.notes-actions,
.notes-item-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.notes-eyebrow {
  display: inline-flex;
  align-items: center;
  min-height: 1.5rem;
  padding: 0 0.625rem;
  border-radius: 62.4375rem;
  background: #e8f7f4;
  color: #168f82;
  font-size: 0.75rem;
  font-weight: 900;
}

.notes-card-head h2,
.notes-list-head h3,
.notes-item h4 {
  margin: 0;
  color: #132933;
}

.notes-card-head h2 {
  margin-top: 0.5rem;
  font-size: 1.5rem;
}

.notes-card-head p,
.notes-list-head span,
.notes-item small {
  color: #71808d;
  font-size: 0.8125rem;
}

.notes-card-head p {
  margin: 0.375rem 0 0;
}

.notes-summary-grid {
  margin: 1rem 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.75rem;
}

.notes-summary-grid > div {
  padding: 0.875rem;
  border-radius: 0.625rem;
  background: #f7fbfa;
}

.notes-summary-grid span {
  color: #71808d;
  font-size: 0.8125rem;
}

.notes-summary-grid strong {
  display: block;
  margin-top: 0.375rem;
  color: #132933;
  font-size: 1.25rem;
  font-weight: 900;
}

.notes-editor-wrap {
  border: 0.0625rem solid #dfeceb;
  border-radius: 0.625rem;
  overflow: hidden;
}

.notes-editor {
  min-height: 21rem;
  background: #fff;
}

.notes-scroll-list {
  margin-top: 0.75rem;
  max-height: calc(100vh - 11rem);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 0.625rem;
  scrollbar-width: none;
}

.notes-scroll-list::-webkit-scrollbar {
  display: none;
}

.notes-item {
  padding: 0.875rem;
  border-radius: 0.625rem;
  background: #f8fbfb;
  border: 0.0625rem solid #edf2f4;
  cursor: pointer;
}

.notes-rich-text {
  margin-top: 0.5rem;
  color: #3e505a;
  font-size: 0.875rem;
  line-height: 1.8;
}

.notes-rich-text :deep(p) {
  margin: 0.375rem 0;
}

@media (max-width: 73.75rem) {
  .notes-shell {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 47.5rem) {
  .notes-card-head,
  .notes-actions {
    align-items: flex-start;
    flex-direction: column;
  }

  .notes-summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
