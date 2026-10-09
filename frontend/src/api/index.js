import http from './http'
import router from '@/router'
import { STORAGE_KEYS } from '@/constants'

// 认证 + 用户
export const authApi = {
  /** 账号密码登录：入参 {username,password} → data: {token,userId,username,nickname} */
  login: (data) => http.post('/auth/login', data),
  /** 注册：入参 {username,password,nickname} */
  register: (data) => http.post('/auth/register', data),
  /** 登出（服务端将 token 加入 Redis 黑名单使其立即失效） */
  logout: () => http.post('/auth/logout'),
  /** 第三方授权跳转地址：入参 source → data: {url, state} */
  oauthUrl: (source) => http.get('/auth/oauth/url', { params: { source } }),
  /** 第三方授权登录：入参 {source, code, state} → data: LoginVO */
  oauthLogin: (data) => http.post('/auth/oauth/login', data)
}

export const userApi = {
  info: () => http.get('/user/info'),
  update: (data) => http.put('/user/update', data),
  password: (data) => http.put('/user/password', data)
}

// 知识库
export const kbApi = {
  /** 我的知识库列表 → data: [{id,name,description,isPublic,noteCount}] */
  list: () => http.get('/kb/list'),
  /** 新建知识库 {name,description} */
  add: (data) => http.post('/kb/add', data),
  /** 更新知识库 {id,name,isPublic,description} */
  update: (data) => http.put('/kb/update', data),
  /** 删除知识库（逻辑删除） */
  remove: (id) => http.delete(`/kb/delete/${id}`)
}

// 分类
export const categoryApi = {
  list: (kbId) => http.get('/category/list', { params: { kbId } }),
  add: (data) => http.post('/category/add', data),
  update: (data) => http.put('/category/update', data),
  remove: (id) => http.delete(`/category/delete/${id}`)
}

// 标签
export const tagApi = {
  list: () => http.get('/tag/list'),
  add: (data) => http.post('/tag/add', data),
  remove: (id) => http.delete(`/tag/delete/${id}`)
}

// 笔记
export const noteApi = {
  /** 分页查笔记：{page,size,kbId,categoryId,keyword} → {records,total}；keyword 走 ES 全文检索 */
  list: (params) => http.get('/note/list', { params }),
  /** 笔记详情（含 content 与标签，浏览数 +1） */
  detail: (id) => http.get(`/note/detail/${id}`),
  add: (data) => http.post('/note/add', data),
  update: (data) => http.put('/note/update', data),
  remove: (id) => http.delete(`/note/delete/${id}`),
  restore: (id) => http.put(`/note/restore/${id}`),
  recycle: (params) => http.get('/note/recycle', { params }),
  purge: (id) => http.delete(`/note/purge/${id}`),
  versions: (noteId) => http.get(`/note/versions/${noteId}`),
  versionDetail: (versionId) => http.get(`/note/version/${versionId}`),
  rollback: (versionId) => http.post(`/note/rollback/${versionId}`)
}

// AI 能力
export const aiApi = {
  outline: (data) => http.post('/ai/generate-outline', data),
  polish: (data) => http.post('/ai/polish', data),
  summarize: (data) => http.post('/ai/summarize', data),
  chat: (data) => http.post('/ai/chat', data),
  /** 异步任务（MQ 解耦）：提交入队 → data: {taskId}，支持 params 技能参数 */
  asyncSubmit: (data) => http.post('/ai/async', data),
  /** 查询异步任务状态（轮询）：data: {taskId,status,statusText,result,errorMsg} */
  asyncTask: (taskId) => http.get(`/ai/task/${taskId}`),
  /** 对话历史（Redis 多轮记忆，按笔记隔离）→ data: [{role:'user'|'assistant', content}] */
  chatHistory: (noteId) => http.get('/ai/chat/history', { params: { noteId } }),
  /** 清空对话记忆（按笔记隔离） */
  clearChatHistory: (noteId) => http.delete('/ai/chat/history', { params: { noteId } }),

  /**
   * AI 对话 SSE 流式：逐字回调 onDelta，结束后返回完整回答。
   * 用 fetch 而非 axios/EventSource：需要流式读 body + 自定义 Authorization 头。
   * @param payload {noteId,title,content,question}
   * @param opts {onDelta(text), signal} signal 为 AbortController.signal，可中断生成
   */
  chatStream: async (payload, { onDelta, signal } = {}) => {
    const token = localStorage.getItem(STORAGE_KEYS.TOKEN)
    const res = await fetch('/api/ai/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: JSON.stringify(payload),
      signal
    })
    if (!res.ok) throw new Error(`请求失败（${res.status}）`)

    // 非流式响应（认证失败等业务码 JSON）：统一按业务结果处理
    const ctype = res.headers.get('content-type') || ''
    if (ctype.includes('application/json')) {
      const body = await res.json()
      if (body.code === 401) {
        localStorage.removeItem(STORAGE_KEYS.TOKEN)
        router.push('/login')
        throw new Error('登录已过期，请重新登录')
      }
      throw new Error(body.message || '请求失败')
    }
    if (!res.body) throw new Error('当前浏览器不支持流式输出')

    const reader = res.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buf = ''
    let full = ''
    let finished = false   // 收到 done 事件即结束，不依赖服务器关闭连接

    /** 处理单个 SSE 事件块（event:名称 + data:负载） */
    const handleEvent = (name, data) => {
      if (name === 'delta') {
        try {
          const t = JSON.parse(data).t
          if (t) { full += t; onDelta?.(t) }
        } catch { /* 忽略坏帧 */ }
      } else if (name === 'done') {
        try {
          const content = JSON.parse(data).content
          if (content) full = content
        } catch { /* 忽略坏帧 */ }
        finished = true
      } else if (name === 'error') {
        let msg = 'AI 处理失败'
        try { msg = JSON.parse(data).message || msg } catch { /* 保底 */ }
        finished = true
        throw new Error(msg)
      }
    }

    try {
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buf += decoder.decode(value, { stream: true })
        // SSE 事件以空行分隔
        let idx
        while ((idx = buf.indexOf('\n\n')) >= 0) {
          const raw = buf.slice(0, idx)
          buf = buf.slice(idx + 2)
          let name = 'message'
          const dataLines = []
          for (const line of raw.split('\n')) {
            if (line.startsWith('event:')) name = line.slice(6).trim()
            else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
          }
          if (dataLines.length) handleEvent(name, dataLines.join('\n'))
          // done 已收到：主动结束，不等服务器关连接（避免 read 悬挂）
          if (finished) {
            reader.cancel().catch(() => {})
            return full
          }
        }
      }
    } catch (e) {
      // 服务器断开（terminated）等：若已拿到 done 内容则正常返回，否则抛错
      if (finished) return full
      if (e.name === 'AbortError') throw e
      throw new Error(e.message || '流式读取中断')
    }
    return full
  }
}

// 文件
export const fileApi = {
  // 传文件：用 FormData，axios 会自动设置带 boundary 的 multipart 头；
  // 拦截器会注入 Authorization，无需手动设 Content-Type
  upload: (file) => {
    const form = new FormData()
    form.append('file', file)
    return http.post('/file/upload', form)
  },
  list: (params) => http.get('/file/list', { params }),
  bind: (fileId, noteId) =>
    http.put('/file/bind', null, { params: { fileId, noteId } }),
  remove: (id) => http.delete(`/file/delete/${id}`)
}

// 公开分享（无需登录）
export const publicApi = {
  kb: (id) => http.get(`/public/kb/${id}`),
  notes: (id, keyword) => http.get(`/public/kb/${id}/notes`, { params: { keyword } }),
  note: (id) => http.get(`/public/note/${id}`)
}

// 统计 / 日志 / 导出
export const statsApi = {
  overview: () => http.get('/stats/overview')
}

export const logApi = {
  list: (params) => http.get('/log/list', { params })
}

export const exportApi = {
  markdown: (noteId) => http.get(`/export/markdown/${noteId}`)
}