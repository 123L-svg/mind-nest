import { ref } from 'vue'
import { STORAGE_KEYS, THEME } from '@/constants'

/**
 * 主题管理：明/暗模式切换与记忆。
 * 单例状态，多个组件共享同一主题；DOM 通过 html[data-theme] 交样式表（tokens/dark.css）。
 */
const theme = ref(localStorage.getItem(STORAGE_KEYS.THEME) || THEME.LIGHT)

function setTheme(t) {
  const next = t === THEME.DARK ? THEME.DARK : THEME.LIGHT
  theme.value = next
  document.documentElement.dataset.theme = next
  localStorage.setItem(STORAGE_KEYS.THEME, next)
}

export function useTheme() {
  /** 应用启动/或外部恢复主题 */
  function init(t) {
    setTheme(t || localStorage.getItem(STORAGE_KEYS.THEME) || THEME.LIGHT)
  }

  function toggle() {
    setTheme(theme.value === THEME.DARK ? THEME.LIGHT : THEME.DARK)
  }

  return { theme, init, toggle }
}