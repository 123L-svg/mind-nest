/**
 * 通用格式化工具（跨页面收敛）。
 */

/**
 * HTML 转义，防 XSS。
 * @param {*} s
 * @returns {string}
 */
export function escapeHtml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/**
 * 渲染 ES 搜索高亮片段：先整体转义防 XSS，再还原 <em>/</em> 以正常高亮。
 * @param {string} text 后端返回的 <em>...</em> 片段
 * @returns {string}
 */
export function hlHtml(text) {
  if (!text) return ''
  return escapeHtml(text)
    .replace(/&lt;em&gt;/g, '<em>')
    .replace(/&lt;\/em&gt;/g, '</em>')
}

/**
 * 格式化为日期时间（秒级截断），兼容后端默认 "yyyy-MM-dd HH:mm:ss"。
 * @param {string|undefined|null} v
 * @returns {string}
 */
export function formatDateTime(v) {
  if (!v) return ''
  const s = String(v)
  return s.length > 19 ? s.slice(0, 19) : s
}

/**
 * 格式化为日期（日级截断）。
 * @param {string|undefined|null} v
 * @returns {string}
 */
export function formatDate(v) {
  if (!v) return ''
  return String(v).slice(0, 10)
}

/**
 * 取用户昵称首字符（头像占位）。
 * @param {{nickname?:string, username?:string}|undefined|null} user
 * @returns {string}
 */
export function initials(user) {
  return (user?.nickname || user?.username || '?').slice(0, 1)
}