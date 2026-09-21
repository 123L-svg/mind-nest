import http from './http'

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
  /** 异步任务（MQ 解耦）：提交入队 → data: {taskId} */
  asyncSubmit: (data) => http.post('/ai/async', data),
  /** 查询异步任务状态（轮询）：data: {taskId,status,statusText,result,errorMsg} */
  asyncTask: (taskId) => http.get(`/ai/task/${taskId}`)
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