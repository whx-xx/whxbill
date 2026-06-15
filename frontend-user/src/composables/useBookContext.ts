import { ref } from 'vue'
import request from '@/utils/request'

const books = ref<any[]>([])
const currentBookId = ref<number>()
const loading = ref(false)
let loaded = false

export function useBookContext() {
  async function loadBooks(force = false) {
    if (loading.value) return
    if (loaded && !force) return

    loading.value = true
    try {
      const result = await request.get<any[]>('/api/books')
      books.value = result
      const availableIds = new Set(result.map((item) => item.id))
      if (!currentBookId.value || !availableIds.has(currentBookId.value)) {
        currentBookId.value = result.find((item) => item.isDefault === 1)?.id || result[0]?.id
      }
      loaded = true
    } finally {
      loading.value = false
    }
  }

  function setCurrentBookId(bookId?: number) {
    currentBookId.value = bookId
  }

  return {
    books,
    currentBookId,
    loading,
    loadBooks,
    setCurrentBookId
  }
}
