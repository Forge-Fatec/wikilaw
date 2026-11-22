import { useSyncExternalStore } from 'react'
import { getTheme, setTheme, subscribeTheme } from '../theme'

export function ThemeToggle() {
  const theme = useSyncExternalStore(subscribeTheme, getTheme)
  const dark = theme === 'dark'
  const label = dark ? 'Ativar modo claro' : 'Ativar modo escuro'
  return (
    <button type="button" className="theme-toggle" onClick={() => setTheme(dark ? 'light' : 'dark')}
      aria-label={label} aria-pressed={dark} title={label}>
      <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true">
        {!dark ? <><circle cx="12" cy="12" r="4" /><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5" /></>
          : <path d="M20.5 13.5A8.5 8.5 0 0 1 10.5 3.5a8.5 8.5 0 1 0 10 10Z" />}
      </svg>
      <span>{dark ? 'Modo escuro' : 'Modo claro'}</span>
    </button>
  )
}
