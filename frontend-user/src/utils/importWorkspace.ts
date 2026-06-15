export const IMPORT_WORKSPACE_KEY = 'whx_bill_import_workspace'
export const IMPORT_WORKSPACE_CHANGED_EVENT = 'whx-bill-import-workspace-changed'

export interface ImportWorkspaceState {
  targetBookId?: number
  preview?: unknown
  rows: any[]
  activeRowNo?: number
}

export function readImportWorkspace(): ImportWorkspaceState | null {
  const cached = sessionStorage.getItem(IMPORT_WORKSPACE_KEY)
  if (!cached) return null
  try {
    const data = JSON.parse(cached)
    const rows = Array.isArray(data.rows) ? data.rows : []
    if (!rows.length) {
      clearImportWorkspace()
      return null
    }
    return { ...data, rows }
  } catch {
    clearImportWorkspace()
    return null
  }
}

export function writeImportWorkspace(state: ImportWorkspaceState | null) {
  if (!state || !state.preview || !state.rows.length) {
    clearImportWorkspace()
    return
  }
  sessionStorage.setItem(IMPORT_WORKSPACE_KEY, JSON.stringify(state))
  notifyImportWorkspaceChanged()
}

export function clearImportWorkspace() {
  sessionStorage.removeItem(IMPORT_WORKSPACE_KEY)
  notifyImportWorkspaceChanged()
}

export function getImportWorkspaceRowCount() {
  return readImportWorkspace()?.rows.length || 0
}

function notifyImportWorkspaceChanged() {
  window.dispatchEvent(new Event(IMPORT_WORKSPACE_CHANGED_EVENT))
}
