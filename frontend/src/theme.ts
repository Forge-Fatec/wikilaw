export type Theme = 'light' | 'dark'
export const THEME_STORAGE_KEY = 'wikilaw:theme'
const THEME_EVENT = 'wikilaw:theme-change'

export function resolveTheme(preference: unknown, systemDark: boolean): Theme {
  return preference === 'light' || preference === 'dark'
    ? preference : systemDark ? 'dark' : 'light'
}

function readPreference(): Theme | null {
  try {
    const value = localStorage.getItem(THEME_STORAGE_KEY)
    return value === 'light' || value === 'dark' ? value : null
  } catch {
    return null
  }
}

let preference = readPreference()

export function getTheme(): Theme {
  return document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light'
}

function applyTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme
  window.dispatchEvent(new Event(THEME_EVENT))
}

export function setTheme(theme: Theme) {
  preference = theme
  try { localStorage.setItem(THEME_STORAGE_KEY, theme) } catch { /* Keep the choice for this page. */ }
  applyTheme(theme)
}

export function subscribeTheme(listener: () => void): () => void {
  const media = window.matchMedia('(prefers-color-scheme: dark)')
  const systemChanged = () => {
    if (preference === null) applyTheme(resolveTheme(null, media.matches))
  }
  const storageChanged = (event: StorageEvent) => {
    if (event.key !== THEME_STORAGE_KEY && event.key !== null) return
    preference = readPreference()
    applyTheme(resolveTheme(preference, media.matches))
  }
  window.addEventListener(THEME_EVENT, listener)
  window.addEventListener('storage', storageChanged)
  media.addEventListener('change', systemChanged)
  // Also reconcile changes that happened while the toggle was unmounted.
  applyTheme(resolveTheme(preference, media.matches))
  return () => {
    window.removeEventListener(THEME_EVENT, listener)
    window.removeEventListener('storage', storageChanged)
    media.removeEventListener('change', systemChanged)
  }
}
