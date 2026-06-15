<template>
  <div class="notes-shell">
    <section class="notes-editor-card">
      <div class="notes-card-head">
        <div>
          <span class="notes-eyebrow">富文本笔记</span>
          <h2>公告与笔记</h2>
        </div>
        <div class="notes-actions">
          <el-button @click="resetDraft">清空</el-button>
          <el-button type="primary" :loading="saving" @click="saveNote">保存笔记</el-button>
        </div>
      </div>

      <div class="notes-summary-grid">
        <div>
          <span>公告数量</span>
          <strong>{{ notices.length }}</strong>
        </div>
        <div>
          <span>我的笔记</span>
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

    <aside class="notes-side">
      <section class="notes-list-card">
        <div class="notes-list-head">
          <h3>最新公告</h3>
          <el-button :icon="Refresh" text type="primary" :loading="loadingNotices" @click="loadNotices">刷新</el-button>
        </div>
        <el-empty v-if="notices.length === 0" description="暂无公告" />
        <div v-else class="notes-scroll-list">
          <article v-for="notice in notices" :key="notice.id" class="notes-item">
            <h4>{{ notice.title }}</h4>
            <div class="notes-rich-text" v-html="sanitizeHtml(notice.content)"></div>
          </article>
        </div>
      </section>

      <section class="notes-list-card">
        <div class="notes-list-head">
          <h3>我的笔记</h3>
          <span>{{ notes.length }} 条</span>
        </div>
        <el-empty v-if="notes.length === 0" description="暂无笔记" />
        <div v-else class="notes-scroll-list">
          <article v-for="note in notes" :key="note.id" class="notes-item clickable" @click="loadNote(note)">
            <div class="notes-item-title">
              <h4>{{ note.title }}</h4>
              <el-button :icon="Delete" text type="danger" @click.stop="deleteNote(note.id)" />
            </div>
            <small>{{ note.updatedAt }}</small>
            <div class="notes-rich-text" v-html="sanitizeHtml(note.content)"></div>
          </article>
        </div>
      </section>
    </aside>
  </div>
</template>

<script setup lang="ts">
import Quill from 'quill'
import 'quill/dist/quill.snow.css'
import { Delete, Refresh } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import request from '@/utils/request'

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
const notices = ref<any[]>([])
const notes = ref<SavedNote[]>([])
const saving = ref(false)
const loadingNotices = ref(false)
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
  await loadNotices()
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
  if (quillRef.value) {
    quillRef.value.setText('')
  }
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
  if (draft.id === noteId) {
    resetDraft()
  }
  ElMessage.success('笔记已删除')
}

async function loadNotices() {
  loadingNotices.value = true
  try {
    notices.value = await request.get('/api/user/notices')
  } finally {
    loadingNotices.value = false
  }
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
