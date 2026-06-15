<template>
  <div class="ocr-shell" :class="{ 'has-draft': Boolean(draft) }">
    <section class="ocr-head">
      <div>
        <div class="manage-section-eyebrow">OCR</div>
        <h2>OCR 管理</h2>
        <p>上传票据、截图或账单图片，识别后确认关键字段并直接添加为账单。</p>
      </div>
      <div class="ocr-head-actions">
        <el-button :icon="Refresh" :disabled="recognizing || generating || savingBill" @click="resetWorkspace">清空</el-button>
        <el-button type="primary" :icon="Document" :loading="generating" @click="generateDraft">重新解析</el-button>
      </div>
    </section>

    <section class="ocr-workbench">
      <aside class="ocr-source-panel">
        <el-upload
          drag
          :auto-upload="false"
          :show-file-list="false"
          accept="image/*"
          :on-change="onFileChange"
        >
          <el-icon class="ocr-upload-icon"><UploadFilled /></el-icon>
          <div class="ocr-upload-title">拖拽或点击上传票据图片</div>
          <div class="ocr-upload-tip">自动放大、灰度增强后识别，建议上传清晰原图</div>
        </el-upload>

        <div v-if="previewUrl" class="ocr-preview-card">
          <div class="ocr-mini-head">
            <strong>原图预览</strong>
            <el-tag effect="plain">{{ recognizing ? '识别中' : '已上传' }}</el-tag>
          </div>
          <img :src="previewUrl" alt="OCR preview" />
        </div>

        <div class="ocr-status-grid">
          <div>
            <span>识别状态</span>
            <strong>{{ recognizing ? '识别中' : recognizedText.trim() ? '已识别' : '待上传' }}</strong>
          </div>
          <div>
            <span>文本行数</span>
            <strong>{{ textLineCount }}</strong>
          </div>
        </div>
      </aside>

      <main class="ocr-text-panel">
        <div class="ocr-panel-head">
          <div>
            <h3>识别文本</h3>
            <span>识别结果不准时，可以在这里手动修正后重新解析</span>
          </div>
          <el-tag v-if="recognizedText.trim()" effect="plain">{{ recognizedText.length }} 字</el-tag>
        </div>
        <el-input
          v-model="recognizedText"
          type="textarea"
          :rows="draft ? 10 : 18"
          resize="none"
          placeholder="OCR 识别结果会显示在这里，也可以直接粘贴票据文本"
        />
      </main>

      <aside v-if="draft" class="ocr-confirm-panel">
        <div class="ocr-confirm-hero" :class="billForm.billType === 'INCOME' ? 'income' : 'expense'">
          <div>
            <span>{{ billForm.billType === 'INCOME' ? '收入账单' : '支出账单' }}</span>
            <strong>{{ formatCurrency(billForm.amount) }}</strong>
            <small>{{ billForm.merchantName || 'OCR 账单' }}</small>
          </div>
          <el-button type="primary" size="large" :loading="savingBill" @click="saveOcrBill">添加账单</el-button>
        </div>

        <el-form ref="billFormRef" :model="billForm" :rules="billRules" label-position="top">
          <div class="ocr-bill-form-grid">
            <el-form-item label="账本" prop="bookId">
              <el-select v-model="billForm.bookId" style="width: 100%">
                <el-option v-for="book in books" :key="book.id" :label="book.bookName" :value="book.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="收支类型" prop="billType">
              <el-segmented
                v-model="billForm.billType"
                :options="[
                  { label: '支出', value: 'EXPENSE' },
                  { label: '收入', value: 'INCOME' }
                ]"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="金额" prop="amount">
              <el-input-number v-model="billForm.amount" :precision="2" :min="0.01" style="width: 100%" />
            </el-form-item>
            <el-form-item label="账单时间" prop="billTime">
              <el-date-picker
                v-model="billForm.billTime"
                type="datetime"
                value-format="YYYY-MM-DDTHH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="账户" prop="accountId">
              <el-select v-model="billForm.accountId" filterable style="width: 100%" placeholder="请选择账户">
                <el-option v-for="account in accounts" :key="account.id" :label="account.accountName" :value="account.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="分类" prop="categoryId">
              <el-select v-model="billForm.categoryId" filterable style="width: 100%" placeholder="请选择分类">
                <el-option v-for="category in categoriesByType" :key="category.id" :label="category.categoryName" :value="category.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="商户/摘要" prop="merchantName" class="ocr-form-full">
              <el-input v-model.trim="billForm.merchantName" maxlength="60" />
            </el-form-item>
            <el-form-item label="备注" class="ocr-form-full">
              <el-input v-model="billForm.remark" type="textarea" :rows="3" resize="none" />
            </el-form-item>
          </div>
        </el-form>

        <div class="ocr-evidence-card">
          <div class="ocr-mini-head">
            <strong>识别依据</strong>
            <el-tag effect="plain">OCR导入</el-tag>
          </div>
          <div class="ocr-evidence-grid">
            <div>
              <span>识别账户</span>
              <strong>{{ draft.accountName || draft.paymentMethod || '-' }}</strong>
            </div>
            <div>
              <span>支付时间</span>
              <strong>{{ displayBillTime }}</strong>
            </div>
            <div>
              <span>商户全称</span>
              <strong>{{ draft.merchantFullName || '-' }}</strong>
            </div>
            <div>
              <span>交易单号</span>
              <strong>{{ draft.transactionNo || '-' }}</strong>
            </div>
          </div>
          <el-alert
            v-if="draft.confidenceTips?.length"
            class="ocr-tips"
            type="warning"
            :closable="false"
            :title="`仍需核对：${draft.confidenceTips.join('、')}`"
          />
        </div>
      </aside>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Document, Refresh, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import Tesseract from 'tesseract.js'
import { useBookContext } from '@/composables/useBookContext'
import request from '@/utils/request'

interface OcrDraft {
  merchantName: string
  productName?: string
  merchantFullName?: string
  paymentMethod?: string
  accountName?: string
  billTime?: string
  transactionNo?: string
  merchantOrderNo?: string
  amount: string
  signedAmount?: string
  suggestedType?: 'EXPENSE' | 'INCOME'
  confidenceTips?: string[]
  text?: string
}

const { books, currentBookId, loadBooks } = useBookContext()
const recognizedText = ref('')
const draft = ref<OcrDraft | null>(null)
const recognizing = ref(false)
const generating = ref(false)
const savingBill = ref(false)
const previewUrl = ref('')
const accounts = ref<any[]>([])
const categories = ref<any[]>([])
const billFormRef = ref<FormInstance>()

const billForm = reactive({
  bookId: undefined as number | undefined,
  accountId: undefined as number | undefined,
  categoryId: undefined as number | undefined,
  billType: 'EXPENSE',
  amount: 0.01,
  billDate: currentDate(),
  billTime: '',
  merchantName: '',
  remark: '',
  sourceType: 'OCR'
})

const billRules: FormRules = {
  bookId: [{ required: true, message: '请选择账本', trigger: 'change' }],
  accountId: [{ required: true, message: '请选择账户', trigger: 'change' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  billType: [{ required: true, message: '请选择收支类型', trigger: 'change' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'change' }],
  billTime: [{ required: true, message: '请选择账单时间', trigger: 'change' }],
  merchantName: [{ required: true, message: '请输入商户或摘要', trigger: 'blur' }]
}

const textLineCount = computed(() => recognizedText.value.split(/\r?\n/).filter((line) => line.trim()).length)
const categoriesByType = computed(() => categories.value.filter((item) => item.categoryType === billForm.billType))
const displayBillTime = computed(() => billForm.billTime ? billForm.billTime.replace('T', ' ') : '-')

async function onFileChange(file: UploadFile) {
  if (!file.raw || recognizing.value) return
  if (!file.raw.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  recognizing.value = true
  draft.value = null
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = URL.createObjectURL(file.raw)
  try {
    const processedImage = await preprocessImage(file.raw)
    const result = await Tesseract.recognize(processedImage, 'chi_sim+eng', {
      tessedit_pageseg_mode: '6',
      preserve_interword_spaces: '1'
    } as any)
    recognizedText.value = normalizeOcrText(result.data.text)
    if (!recognizedText.value.trim()) {
      ElMessage.warning('未识别到有效文本，可以手动输入后生成草稿')
    } else {
      ElMessage.success('OCR 识别完成，已做文本清理')
      await generateDraft()
    }
  } catch {
    ElMessage.error('OCR 识别失败，请换一张更清晰的图片重试')
  } finally {
    recognizing.value = false
  }
}

async function generateDraft() {
  const text = normalizeOcrText(recognizedText.value)
  if (!text) {
    ElMessage.warning('请先上传票据或输入识别文本')
    return
  }
  recognizedText.value = text
  if (generating.value) return
  generating.value = true
  try {
    const result = await request.post('/api/ocr/draft', { text })
    const localDraft = parseDraftLocally(text)
    const nextDraft = {
      ...result,
      ...localDraft,
      confidenceTips: result.confidenceTips || localDraft.confidenceTips
    } as OcrDraft
    draft.value = nextDraft
    await hydrateBillFormFromDraft(nextDraft)
    ElMessage.success('草稿账单已生成')
  } finally {
    generating.value = false
  }
}

async function hydrateBillFormFromDraft(row: OcrDraft) {
  if (!currentBookId.value) await loadBooks()
  await loadReferenceData()
  const billType = row.suggestedType || 'EXPENSE'
  const billTime = toInputDateTime(row.billTime) || `${currentDate()}T${currentTime()}`
  billForm.bookId = currentBookId.value
  billForm.billType = billType
  billForm.amount = Math.max(0.01, Number(row.amount || 0))
  billForm.billTime = billTime
  billForm.billDate = billTime.slice(0, 10)
  billForm.merchantName = row.productName || row.merchantName || row.merchantFullName || 'OCR 账单'
  billForm.accountId = matchAccountId(row.accountName || row.paymentMethod)
  billForm.categoryId = inferCategoryId(row)
  billForm.remark = [
    row.merchantFullName ? `商户：${row.merchantFullName}` : '',
    row.transactionNo ? `交易单号：${row.transactionNo}` : '',
    row.merchantOrderNo ? `商户单号：${row.merchantOrderNo}` : '',
    '记录方式：OCR导入'
  ].filter(Boolean).join('\n')
  billForm.sourceType = 'OCR'
}

async function saveOcrBill() {
  if (!draft.value || savingBill.value) return
  if (!billForm.accountId) {
    billForm.accountId = await ensureRecognizedAccount(draft.value.accountName || draft.value.paymentMethod)
  }
  const valid = await billFormRef.value?.validate().catch(() => false)
  if (!valid) return
  savingBill.value = true
  try {
    if (!billForm.accountId || !billForm.categoryId) {
      ElMessage.warning('请先确认账户和分类')
      return
    }
    const payload = {
      ...billForm,
      billDate: billForm.billTime.slice(0, 10),
      sourceType: 'OCR'
    }
    await request.post('/api/bills', payload)
    ElMessage.success('OCR 账单已添加')
    resetWorkspace()
  } finally {
    savingBill.value = false
  }
}

async function ensureRecognizedAccount(name?: string) {
  const accountName = normalizeAccountName(name)
  if (!accountName || !billForm.bookId) return undefined
  const existed = accounts.value.find((item) => normalizeText(item.accountName).includes(normalizeText(accountName)))
  if (existed) return existed.id
  const result = await request.post('/api/accounts', {
    bookId: billForm.bookId,
    accountName,
    accountType: inferAccountType(accountName),
    balance: 0,
    colorTag: inferAccountColor(accountName),
    sortOrder: accounts.value.length
  })
  await loadReferenceData()
  ElMessage.success(`已自动创建账户：${accountName}`)
  return result?.id || accounts.value.find((item) => item.accountName === accountName)?.id
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

function normalizeOcrText(text: string) {
  return text
    .replace(/\u00a0/g, ' ')
    .replace(/(?<=[\u4e00-\u9fa5])\s+(?=[\u4e00-\u9fa5])/g, '')
    .replace(/[：]/g, ':')
    .replace(/[—–]/g, '-')
    .split(/\r?\n/)
    .map((line) => line.replace(/\s{2,}/g, ' ').trim())
    .filter(Boolean)
    .join('\n')
}

function parseDraftLocally(text: string): OcrDraft {
  const lines = text.split(/\r?\n/).map((line) => line.trim()).filter(Boolean)
  const productName = findValue(lines, ['商品', '商品名称']) || inferProduct(lines)
  const merchantFullName = findValue(lines, ['商户全称', '商户名称', '商户'])
  const paymentMethod = findValue(lines, ['支付方式', '付款方式', '收支账户', '账户'])
  const billTime = normalizeDateTime(findValue(lines, ['支付时间', '交易时间', '时间']))
  const amountGuess = guessAmount(lines)
  return {
    merchantName: productName || merchantFullName || 'OCR 草稿',
    productName,
    merchantFullName,
    paymentMethod,
    accountName: paymentMethod,
    billTime,
    transactionNo: findValue(lines, ['交易单号', '交易号', '流水号']),
    merchantOrderNo: findValue(lines, ['商户单号', '商家单号', '订单号']),
    amount: amountGuess.amount,
    signedAmount: amountGuess.signedAmount,
    suggestedType: Number(amountGuess.signedAmount) < 0 || !/(收入|收款|到账|转入)/.test(text) ? 'EXPENSE' : 'INCOME',
    confidenceTips: []
  }
}

function findValue(lines: string[], labels: string[]) {
  for (let index = 0; index < lines.length; index += 1) {
    const compact = lines[index].replace(/\s+/g, '')
    for (const label of labels) {
      const position = compact.indexOf(label)
      if (position < 0) continue
      const value = compact.slice(position + label.length).replace(/^[:：-]+/, '').trim()
      if (value) return value
      return lines[index + 1]?.trim() || ''
    }
  }
  return ''
}

function guessAmount(lines: string[]) {
  for (const line of lines) {
    const signed = line.replace(/\s+/g, '').match(/([+-])(?:¥|￥)?(\d+(?:\.\d{1,2})?)/)
    if (signed) {
      return {
        amount: Number(signed[2]).toFixed(2),
        signedAmount: `${signed[1] === '-' ? '-' : ''}${Number(signed[2]).toFixed(2)}`
      }
    }
  }
  const fallback = lines.join(' ').match(/(?:¥|￥)?\s*(\d+(?:\.\d{1,2})?)/)
  const amount = Number(fallback?.[1] || 0).toFixed(2)
  return { amount, signedAmount: amount }
}

function inferProduct(lines: string[]) {
  return lines.find((line) =>
    /[\u4e00-\u9fa5]/.test(line)
    && line.length <= 24
    && !/[A-Z0-9]{8,}/.test(line)
    && !/(支付|状态|单号|商户|机构|方式|时间)/.test(line)
  ) || ''
}

function normalizeDateTime(value: string) {
  const normalized = value
    .replace('年', '-')
    .replace('月', '-')
    .replace('日', ' ')
    .replace(/\./g, ':')
    .replace(/\s+/g, ' ')
    .trim()
  const matched = normalized.match(/(\d{4})-(\d{1,2})-(\d{1,2})\s*(\d{1,2})[:\-](\d{1,2})[:\-](\d{1,2})/)
  if (!matched) return normalized
  return `${matched[1]}-${matched[2].padStart(2, '0')}-${matched[3].padStart(2, '0')} ${matched[4].padStart(2, '0')}:${matched[5].padStart(2, '0')}:${matched[6].padStart(2, '0')}`
}

function toInputDateTime(value?: string) {
  if (!value) return ''
  return value.includes('T') ? value.slice(0, 19) : value.replace(' ', 'T').slice(0, 19)
}

function currentDate() {
  const date = new Date()
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function currentTime() {
  const date = new Date()
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}:${String(date.getSeconds()).padStart(2, '0')}`
}

function matchAccountId(name?: string) {
  const keyword = normalizeText(normalizeAccountName(name))
  if (!keyword) return undefined
  return accounts.value.find((item) => normalizeText(item.accountName).includes(keyword) || keyword.includes(normalizeText(item.accountName)))?.id
}

function normalizeAccountName(name?: string) {
  const value = String(name || '').trim()
  if (!value || value === '-') return ''
  if (value.includes('零钱通')) return '零钱通'
  if (value.includes('微信')) return '微信钱包'
  if (value.includes('支付宝')) return '支付宝'
  if (value.includes('银行卡') || value.includes('银行')) return '银行卡'
  return value.replace(/^[:：-]+/, '')
}

function inferAccountType(name: string) {
  if (/微信|零钱通/.test(name)) return 'WECHAT'
  if (/支付宝|余额宝/.test(name)) return 'ALIPAY'
  if (/银行|卡/.test(name)) return 'BANK'
  return 'CASH'
}

function inferAccountColor(name: string) {
  if (/微信|零钱通/.test(name)) return '#43C66A'
  if (/支付宝|余额宝/.test(name)) return '#1677FF'
  if (/银行|卡/.test(name)) return '#4F8EF7'
  return '#26A69A'
}

function inferCategoryId(row: OcrDraft) {
  const sameType = categories.value.filter((item) => item.categoryType === (row.suggestedType || 'EXPENSE'))
  const text = `${row.productName || ''} ${row.merchantName || ''} ${row.merchantFullName || ''}`
  const hour = Number((row.billTime || '').match(/\s(\d{1,2}):/)?.[1] || -1)
  const candidates = []
  if (/饭|餐|吃|食|烤鸭|面|粉|外卖|美团|饿了么/.test(text)) {
    if (hour >= 16) candidates.push('晚餐')
    else if (hour >= 10) candidates.push('午餐')
    else if (hour >= 0) candidates.push('早餐')
    candidates.push('食品餐饮', '餐饮')
  }
  candidates.push('其他')
  for (const name of candidates) {
    const found = sameType.find((item) => String(item.categoryName || '').includes(name))
    if (found) return found.id
  }
  return sameType[0]?.id
}

function normalizeText(value?: string) {
  return String(value || '').replace(/\s+/g, '').toLowerCase()
}

function formatCurrency(value: number | string) {
  return `¥${Number(value || 0).toFixed(2)}`
}

async function preprocessImage(file: File) {
  const bitmap = await createImageBitmap(file)
  const scale = Math.min(3, Math.max(1.5, 1800 / bitmap.width))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(bitmap.width * scale)
  canvas.height = Math.round(bitmap.height * scale)
  const ctx = canvas.getContext('2d')
  if (!ctx) return file
  ctx.fillStyle = '#fff'
  ctx.fillRect(0, 0, canvas.width, canvas.height)
  ctx.imageSmoothingEnabled = true
  ctx.imageSmoothingQuality = 'high'
  ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height)
  const data = imageData.data
  for (let index = 0; index < data.length; index += 4) {
    const gray = data[index] * 0.299 + data[index + 1] * 0.587 + data[index + 2] * 0.114
    const boosted = Math.max(0, Math.min(255, (gray - 128) * 1.35 + 128))
    data[index] = boosted
    data[index + 1] = boosted
    data[index + 2] = boosted
  }
  ctx.putImageData(imageData, 0, 0)
  return await new Promise<Blob>((resolve) => {
    canvas.toBlob((blob) => resolve(blob || file), 'image/png', 0.96)
  })
}

function resetWorkspace() {
  recognizedText.value = ''
  draft.value = null
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
  previewUrl.value = ''
}

watch(
  () => billForm.billType,
  () => {
    if (billForm.categoryId && !categoriesByType.value.some((item) => item.id === billForm.categoryId)) {
      billForm.categoryId = categoriesByType.value[0]?.id
    }
  }
)

watch(currentBookId, async (value, oldValue) => {
  if (!value || value === oldValue) return
  billForm.bookId = value
  await loadReferenceData()
})

onMounted(async () => {
  await loadBooks()
  billForm.bookId = currentBookId.value
  await loadReferenceData()
})
</script>

<style scoped>
.ocr-shell {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ocr-head,
.ocr-source-panel,
.ocr-text-panel,
.ocr-confirm-panel {
  background: #fff;
  border: 1px solid #e5ebf0;
  border-radius: 8px;
}

.ocr-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 20px;
}

.ocr-head h2,
.ocr-panel-head h3,
.ocr-mini-head strong {
  margin: 0;
  color: #10242f;
}

.ocr-head h2 {
  font-size: 24px;
}

.ocr-head p,
.ocr-panel-head span,
.ocr-status-grid span,
.ocr-evidence-grid span,
.ocr-confirm-hero span,
.ocr-confirm-hero small {
  color: #7d8d9a;
  font-size: 13px;
}

.ocr-head p {
  margin: 8px 0 0;
}

.ocr-head-actions,
.ocr-panel-head,
.ocr-mini-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ocr-workbench {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.ocr-shell.has-draft .ocr-workbench {
  grid-template-columns: 330px minmax(420px, 0.86fr) minmax(430px, 1fr);
}

.ocr-source-panel,
.ocr-text-panel,
.ocr-confirm-panel {
  padding: 16px;
  min-width: 0;
}

.ocr-source-panel,
.ocr-confirm-panel {
  position: sticky;
  top: 82px;
}

.ocr-source-panel :deep(.el-upload-dragger) {
  border-radius: 8px;
  background: #fbfcfd;
}

.ocr-upload-icon {
  color: #26a69a;
  font-size: 42px;
}

.ocr-upload-title {
  margin-top: 8px;
  color: #10242f;
  font-weight: 700;
}

.ocr-upload-tip {
  margin-top: 6px;
  color: #7d8d9a;
  font-size: 13px;
}

.ocr-preview-card {
  margin-top: 14px;
  border: 1px solid #edf1f4;
  border-radius: 8px;
  overflow: hidden;
  background: #f8fafb;
}

.ocr-preview-card .ocr-mini-head {
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid #edf1f4;
}

.ocr-preview-card img {
  display: block;
  width: 100%;
  max-height: 280px;
  object-fit: contain;
}

.ocr-status-grid,
.ocr-evidence-grid,
.ocr-bill-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.ocr-status-grid > div,
.ocr-evidence-grid > div {
  display: grid;
  gap: 6px;
  padding: 12px;
  border-radius: 8px;
  background: #f8fafb;
  min-width: 0;
}

.ocr-status-grid strong,
.ocr-evidence-grid strong {
  color: #10242f;
  font-size: 15px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ocr-panel-head {
  justify-content: space-between;
  margin-bottom: 12px;
}

.ocr-text-panel :deep(.el-textarea__inner) {
  min-height: 390px !important;
  font-family: "Consolas", "Microsoft YaHei", monospace;
  line-height: 1.65;
}

.ocr-confirm-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ocr-confirm-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 16px;
  border-radius: 8px;
  background: #fff7f7;
  border: 1px solid #ffe1e1;
}

.ocr-confirm-hero.income {
  background: #f0fbf7;
  border-color: #caefe2;
}

.ocr-confirm-hero > div {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.ocr-confirm-hero strong {
  color: #ff474d;
  font-size: 28px;
  line-height: 1;
}

.ocr-confirm-hero.income strong {
  color: #15966f;
}

.ocr-confirm-hero small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ocr-form-full {
  grid-column: 1 / -1;
}

.ocr-evidence-card {
  padding-top: 12px;
  border-top: 1px solid #edf1f4;
}

.ocr-tips {
  margin-top: 12px;
}

@media (max-width: 1360px) {
  .ocr-shell.has-draft .ocr-workbench {
    grid-template-columns: 310px minmax(0, 1fr);
  }

  .ocr-confirm-panel {
    grid-column: 1 / -1;
    position: static;
  }
}

@media (max-width: 960px) {
  .ocr-workbench,
  .ocr-shell.has-draft .ocr-workbench {
    grid-template-columns: 1fr;
  }

  .ocr-source-panel {
    position: static;
  }
}

@media (max-width: 720px) {
  .ocr-head,
  .ocr-head-actions,
  .ocr-confirm-hero {
    align-items: stretch;
    flex-direction: column;
  }

  .ocr-head-actions,
  .ocr-head-actions .el-button,
  .ocr-confirm-hero .el-button {
    width: 100%;
  }

  .ocr-bill-form-grid,
  .ocr-evidence-grid {
    grid-template-columns: 1fr;
  }
}
</style>
