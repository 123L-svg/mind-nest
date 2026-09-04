/**
 * 常量收敛：统一关键魔法值与存储 key，避免散落各文件。
 */

/** 本地存储 Key */
export const STORAGE_KEYS = {
  TOKEN: 'token',
  USER: 'user',
  THEME: 'theme'
}

/** 主题 */
export const THEME = {
  LIGHT: 'light',
  DARK: 'dark'
}

/** 资源状态（与后端枚举保持一致） */
export const NOTE_STATUS = {
  NORMAL: 0,   // 正常
  RECYCLE: 1   // 回收站
}

/** 分页默认值 */
export const PAGINATION = {
  PAGE: 1,
  SIZE: 10
}

/** 用户状态 */
export const USER_STATUS = {
  NORMAL: 0,
  DISABLED: 1
}