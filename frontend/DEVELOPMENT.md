# AI 智能笔记系统 · 前端开发文档

> 前端为已引入 Element Plus 的 Vue3 系统。本文档覆盖：环境启动、架构与 UI 体系约定、后端接口契约、关键实现要点与排错。

## 一、项目概述

在线笔记知识库系统前端：知识库管理、富文本笔记编辑、AI 辅助创作、文件上传回显、公开分享、数据统计、笔记导出、第三方登录。

- 前端：Vue 3 + Vite + Element Plus + Pinia + Vue Router + Axios + WangEditor + ECharts
- 后端：Spring Boot 3.2，context-path = `/api`，端口 `8080`

## 二、环境准备与启动

```bash
# 依赖安装（首次）
cd frontend
npm install

# 开发启动（默认 5173，占用自动切 5174）
npm run dev

# 生产构建
npm run build
```

**联调后端二选一：**
- Docker（推荐，当前在跑）：`docker compose up -d`（后端 `localhost:8080`）
- 本地：`cd backend && mvn -s .mvn/settings.xml spring-boot:run`（默认连本机 MySQL `3307`）

**账号**：`tester / 123456`（Docker 环境首次需在登录页注册）。

[Vite 代理配置](file:///d:/VIBEcoding/Trae_sapce/ai-note-system/frontend/vite.config.js) 已把 `/api` 转发到 `localhost:8080`，开发期无跨域问题。

## 三、目录结构

```
frontend/
├── index.html
├── vite.config.js        # 代理 /api -> 8080；@ 别名指向 src
├── nginx.conf            # 生产 Nginx 配置
├── Dockerfile            # 生产镜像（多阶段构建）
└── src/
    ├── main.js           # 入口：挂载 router + pinia，初始化主题
    ├── App.vue           # 仅 <router-view/>
    ├── styles/           # 设计令牌与全局样式
    │   ├── tokens.css     # 明/暗色彩、圆角、阴影 CSS 变量（--c-* / --r-* / --shadow-*）
    │   ├── dark.css       # 深色模式下覆盖 Element Plus 变量（--el-*）
    │   └── index.css      # 全局 reset + 通用工具类 + v-html 高亮样式（入口）
    ├── constants/index.js# 存储key/主题/状态/分页 常量
    ├── utils/format.js   # escapeHtml / hlHtml / formatDateTime / formatDate / initials
    ├── composables/
    │   └── useTheme.js    # 明/暗主题切换与记忆（html[data-theme]）
    ├── components/       # 通用组件：UserBadge / AppEmpty / AppError / AppLoading
    ├── api/
    │   ├── http.js       # axios 实例 + 拦截器（token 注入 / 401 跳转 / 统一错误）
    │   └── index.js      # 各模块 API 封装（含 JSDoc）
    ├── router/index.js   # 路由 + 登录守卫
    ├── store/user.js     # Pinia 用户态（token/user）
    └── views/            # Login / Dashboard / NoteEdit / Profile / Recycle /
                          # Stats / Tag / Category / Share / OAuthCallback
```

## 四、前端架构约定

### 1. HTTP 封装（`src/api/http.js`）

- `baseURL: '/api'`，请求自动带 `Authorization: Bearer <token>`
- 响应拦截器剥壳：`code===200` 返回 `body`（`{code,message,data}`）；**`code===401` 自动清 token 并跳 `/login`**；其余 reject `Error(message)`
- **组件中统一 `try/catch` 拿 `e.message` 提示**，不要重复剥壳

```js
const res = await kbApi.list()   // res.data 即后端 data 字段
```

### 2. 路由与守卫（`src/router/index.js`）

- 公开路由：`login`、`share`、`oauth-callback`
- 其余路由无 token 自动重定向 `/login`；已登录访问 `/login` 跳 `/dashboard`
- 新增页面步骤：views 下建组件 → router 注册（懒加载）→ 导航链接

### 3. 状态管理（`src/store/user.js`）

- `store.login(payload)` 登录并存 token；`store.fetchInfo(force?)` 拉用户信息；`store.logout()` 退出
- token 存 `localStorage`，页面刷新由路由守卫兜底

### 4. 关键约定（务必遵守）

| 约定 | 说明 |
|------|------|
| **Long 主键是字符串** | 后端所有 id（userId/kbId/noteId/fileId 等）序列化为字符串，前端不要做 `+` 数值运算 |
| **content 是 HTML** | 详情/列表返回的 `content` 为富文本 HTML；展示用 `v-html`，编辑走 WangEditor |
| **图片地址是 `/api/files/...`** | `v-html` 渲染后经代理/Nginx 可直接显示，无需二次拼接 |
| **日期格式化** | 统一用 `utils/format` 的 `formatDateTime` / `formatDate`，不要再手写 `slice(0,19)` |
| **高亮 styles 必须放全局** | ES 搜索高亮 `<em>` 经 `v-html` 注入，**scoped 选择器命中不到**；样式需放 `styles/index.css`（`.tl em,.hl em`） |
| **分页结构** | 分页接口返回 `{ records:[], total, size, current, pages }`（total/pages 也是字符串） |
| **主题** | 用 `useTheme`（`html[data-theme]` + localStorage），页面背景/颜色优先取 `var(--c-bg)` 等令牌，勿硬编码浅色值 |

## 五、统一返回格式与错误码

```json
{ "code": 200, "message": "操作成功", "data": { } }
```

| code | 含义 | 前端处理 |
|------|------|---------|
| 200 | 成功 | 取 `data` |
| 400 | 参数错误 | 提示 `message` |
| 401 | 未登录/过期 | 拦截器自动清 token 跳登录 |
| 403 | 无权限 | 提示 |
| 404 | 资源不存在 | 提示 |
| 500 | 系统错误 | 提示 |
| 600 | 业务失败 | 提示 `message` |

## 六、后端接口契约

> 除标注「公开」外均需请求头 `Authorization: Bearer <token>`。
> 所有 id 字段返回为**字符串**；所有请求/响应均为 JSON（上传除外）。

### 6.1 认证 / 用户（公开：认证）

| 方法 | 路径 | 入参 | 返回 data |
|------|------|------|-----------|
| POST | `/api/auth/register` | `{username, password, nickname?}` | null |
| POST | `/api/auth/login` | `{username, password}` | `{token, userId, username, nickname}` |
| GET | `/api/auth/oauth/url` | `?source=github\|gitee` | `{url, state}` |
| POST | `/api/auth/oauth/login` | `{source, code, state}` | `{token, userId, username, nickname}`（首次自动建号） |
| GET | `/api/user/info` | - | `{id, username, nickname, avatar, email}` |
| PUT | `/api/user/update` | `{nickname?, email?}` | null |
| PUT | `/api/user/password` | `{oldPassword, newPassword}` | null |

校验规则：username 3-32 位（字母/数字/下划线）；密码 6-32 位；email 需合法格式。

### 6.2 知识库

| 方法 | 路径 | 入参 | 返回 data |
|------|------|------|-----------|
| GET | `/api/kb/list` | - | `[{id, name, description, cover, isPublic, noteCount, createTime}]` |
| POST | `/api/kb/add` | `{name, description?, cover?, isPublic?}` | kbId |
| PUT | `/api/kb/update` | `{id, name, description?, cover?, isPublic?}` | null |
| DELETE | `/api/kb/delete/{id}` | - | null |
| GET | `/api/kb/detail/{id}` | - | KbVO |

`isPublic`：0 私有 / 1 公开。公开后可生成分享链接 `/share/{kbId}`。

### 6.3 分类 / 标签

| 方法 | 路径 | 入参 | 返回 data |
|------|------|------|-----------|
| GET | `/api/category/list` | `?kbId=` | `[{id, kbId, name, sort}]` |
| POST | `/api/category/add` | `{kbId, name, sort?}` | id |
| PUT | `/api/category/update` | `{id, kbId, name, sort?}` | null |
| DELETE | `/api/category/delete/{id}` | - | null |
| GET | `/api/tag/list` | - | `[{id, name}]` |
| POST | `/api/tag/add` | `{name}` | id |
| DELETE | `/api/tag/delete/{id}` | - | null |

### 6.4 笔记

| 方法 | 路径 | 入参 | 返回 data |
|------|------|------|-----------|
| GET | `/api/note/list` | `?page=1&size=10&kbId=&categoryId=&keyword=` | `Page<NoteVO>`（**不含 content**，含 tags） |
| GET | `/api/note/recycle` | `?page=1&size=10` | `Page<NoteVO>` |
| POST | `/api/note/add` | `{kbId, categoryId?, title, content?, summary?, wordCount?, tagIds?[]}` | noteId |
| PUT | `/api/note/update` | `{id, ...同上}` | null |
| GET | `/api/note/detail/{id}` | - | NoteVO（**含 content**、tags） |
| DELETE | `/api/note/delete/{id}` | - | 移入回收站 |
| PUT | `/api/note/restore/{id}` | - | 恢复 |
| DELETE | `/api/note/purge/{id}` | - | 永久删除 |

NoteVO：`{id, kbId, categoryId, title, content?, summary, wordCount, viewCount, status, tags:[{id,name}], createTime, updateTime}`

> 注意：`content` 保存时后端会做 jsoup 白名单净化（剥离 script/on* 等）。前端提交的 HTML 需为编辑器生成的规范标签（h1-h6/p/strong/em/u/s/blockquote/pre/code/ul/ol/li/img/table/a/div/span/br）。

### 6.5 AI 助手

| 方法 | 路径 | 入参 | 返回 data |
|------|------|------|-----------|
| POST | `/api/ai/generate-outline` | `{title?, content?}` | `{action, model, mock, result}` |
| POST | `/api/ai/polish` | `{content?}` | 同上 |
| POST | `/api/ai/summarize` | `{content?}` | 同上 |
| POST | `/api/ai/chat` | `{content?, question?}` | 同上 |

`mock=true` 表示返回的是模拟结果（后端未配 Key 时）。`result` 为 AI 文本（Markdown/纯文本）。

### 6.6 文件

| 方法 | 路径 | 入参 | 返回 data |
|------|------|------|-----------|
| POST | `/api/file/upload` | multipart `file`（≤10MB，类型白名单） | `{id, noteId, originalName, path, size, type, createTime}` |
| PUT | `/api/file/bind` | `?fileId=&noteId=` | null |
| GET | `/api/file/list` | `?noteId=`（可空） | `[{id, noteId, originalName, path, size, type, createTime}]` |
| DELETE | `/api/file/delete/{id}` | - | null |

`path` 形如 `/files/20260826/{uuid}.png`，拼接前缀即为 `/api/files/...`（图片可直接展示）。

### 6.7 公开分享（无需登录）

| 方法 | 路径 | 入参 | 返回 data |
|------|------|------|-----------|
| GET | `/api/public/kb/{id}` | - | KbVO |
| GET | `/api/public/kb/{id}/notes` | `?keyword=` | `[NoteVO]` |
| GET | `/api/public/note/{id}` | - | NoteVO（含 content） |

仅 `is_public=1` 的知识库可读；内部已校验，未公开返回 404。

### 6.8 统计 / 日志 / 导出

| 方法 | 路径 | 入参 | 返回 data |
|------|------|------|-----------|
| GET | `/api/stats/overview` | - | `{kbCount, noteCount, recycleCount, totalViewCount, recentTitle?, recentUpdateTime?}` |
| GET | `/api/log/list` | `?page=1&size=20` | `Page<LogVO>`（module/action/method/ip/duration/success/errorMsg/createTime） |
| GET | `/api/export/markdown/{noteId}` | - | `Result<String>`（data 为 Markdown 文本） |

## 七、关键功能实现要点

### 1. 富文本编辑器（WangEditor）

参考 [NoteEditView.vue](file:///d:/VIBEcoding/Trae_sapce/ai-note-system/frontend/src/views/NoteEditView.vue)：

```js
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import '@wangeditor/editor/dist/css/style.css'

// 图片上传：走后端 /file/upload，插入 /api + path
const editorConfig = {
  uploadImage: {
    maxFileSize: 10 * 1024 * 1024,
    async customUpload(file, insertFn) {
      const res = await fileApi.upload(file)
      const url = '/api' + res.data.path
      insertFn(url, res.data.originalName || '', url)
    }
  }
}
```

- 编辑器 `v-model` 绑定 `html`，`@onChange` 时用 `editorRef.value.getHtml()` 同步到表单
- 保存后调用 `fileApi.bind(fileId, noteId)` 把内容中引用的图片绑定到笔记（参考 `bindFilesToNote`）

### 2. 笔记保存后绑定文件

编辑页保存流程：`noteApi.add/update` → 用返回的 noteId 遍历未绑定文件，凡 `content.includes(f.path)` 即 `fileApi.bind(f.id, noteId)`。

### 3. 图片回显（v-html）

```html
<div class="rich" v-html="detail.content"></div>
```
content 中的 `<img src="/api/files/...">` 经代理/Nginx 直接可显示；给图片加 `max-width:100%` 样式防溢出。

### 4. 公开分享页

`/share/:kbId` 为公开路由，走 `publicApi`（无需 token）。参考 [ShareView.vue](file:///d:/VIBEcoding/Trae_sapce/ai-note-system/frontend/src/views/ShareView.vue)。分享链接 = `location.origin + '/share/' + kbId`。

### 5. 笔记导出

- Markdown：`exportApi.markdown(noteId)` → `res.data` 为文本，用 `Blob` 下载 `.md`
- PDF：前端把 markdown 简易转 HTML 后经隐藏 iframe `window.print()` 打印（参考 NoteEditView `exportPdf`），无需后端 PDF 库

### 6. 401 全局处理

已由 `http.js` 拦截器统一处理：收到 `code===401` 自动清 token 跳登录。业务代码无需自行判断。

## 八、开发规范

1. **API 调用统一走 `src/api/index.js` 的封装**，新增接口在此追加（写 JSDoc）；禁止在组件内裸写 `axios`
2. 组件内请求用 `try/catch`，成功取 `res.data`，失败 `ElMessage(e.message)`；导入用 `@/` 别名
3. 页面/组件命名：视图 `XxxView.vue`（router 注册名对应 path）
4. 富文本/HTML 一律由 WangEditor 生成或取自后端，**不要手拼不可信 HTML**（后端会净化，但前端也不应注入）
5. 长整型 ID 视为字符串处理；分页参数 `page/size` 传数字
6. 样式用 `styles/tokens.css` 的令牌变量（`var(--c-bg)` 等），组件内 `scoped`；**勿硬编码浅色值**（破坏深色模式）
7. 日期、高亮、转义统一从 `utils/format` 引用，不再内联
8. 列表加载用 `v-loading`；增/删/切换做**乐观更新**时，失败要回滚到原状态再提示

### UI 体系与主题
- 主题状态在 `composables/useTheme`（写 `html[data-theme]` + localStorage），入口 `main.js` 调 `useTheme().init()`
- 明/暗 CSS 变量在 `styles/tokens.css`；Element Plus 深色变量覆盖在 `styles/dark.css`；切换按钮用 `useTheme().toggle()`
- 通用状态组件：`components/AppEmpty` / `AppError` / `AppLoading`
- 第三方登录流程：登录页 GitHub/Gitee → `authApi.oauthUrl` → 跳第三方 → 回调 `oauth-callback?code&state` → `authApi.oauthLogin` 保存 token → 跳 dashboard（见 `OAuthCallbackView`）

### Element Plus 按需引入与体积优化

> 目的：主包从全量 Element Plus（~1.2MB）降到按需（~238KB）。改造后**新增页面直接用 `<el-*>` 即可，无需手动 import 组件或样式**。

- **组件按需**：`vite.config.js` 使用 `unplugin-vue-components` + `ElementPlusResolver`，模板里的 `<el-button>`、`<el-table>` 等自动按需解析组件与对应 CSS（新增即可用）。
- **函数式组件**：`ElMessage` / `ElMessageBox`（`window.$message` / `window.$confirm`）不会自动注入样式，已在 `main.js` 显式 `import` 并手动引入二者 style/css。
- **中文语言包**：因为不再 `app.use(ElementPlus, {locale})`，改为 `App.vue` 用 `<el-config-provider :locale="zhCn">` 包裹。
- **图标按需**：图标不再全局注册，用到的图标需在所在组件 `import { Xxx } from '@element-plus/icons-vue'` 后用于模板（例如 Dashboard 的 `Notebook/Sunny/Moon`）。
- **echarts 按需**：避免 `import * as echarts from 'echarts'`，改用 `echarts/core` + `echarts.use([BarChart, LineChart, PieChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])`（参考 `StatsView`）。新增图表类型记得在 `echarts.use` 里补注册。

## 九、常见问题排错

| 现象 | 排查 |
|------|------|
| 接口 404 | 确认后端已启动且请求路径带 `/api` 前缀（baseURL 已含） |
| 登录后仍跳登录 | 后端返回 `code=401`；确认登录接口本身在 `/auth/**` 公开路径 |
| 图片不显示 | 检查 content 中 img src 是否为 `/api/files/...`；文件是否仍存在（删除/重建卷会丢） |
| 上传报 600 | 后端类型白名单拦截（.exe 等被拒）或超 10MB |
| AI 返回"演示模式" | 后端 `AI_MOCK_ENABLED=true`，`result.mock=true` 属正常，非 Bug |
| 端口被占用 | 5173 被占用会自动用 5174；CORS 白名单已含 5173/5174 |
| 401 死循环 | 检查是否有接口在未登录时被调用且路由不在公开列表 |
| 搜索高亮是灰色 | 高亮 `<em>` 由 `v-html` 注入，scoped 样式不生效；把 `.tl em,.hl em` 样式放全局 `styles/index.css` |
| formatDateTime is not defined | 用到 `utils/format` 的函数须先在组件 `import` |
| 新加的 el-* 组件无样式/未渲染 | Element Plus 按需：请用模板直接 `<el-xxx>`（resolver 自动解析）；函数式组件（ElMessage 等）需在 main.js 已处理，勿在组件内裸用 |
| 新图表类型没显示 | echart 按需需在 `StatsView` 的 `echarts.use([...])` 里补注册对应 series 组件 |

## 十、生产部署

```bash
# 根目录
docker compose up -d --build   # 前端由 Nginx 提供服务（端口 80）
```

Nginx（`frontend/nginx.conf`）：`/api/` 反代到 `backend:8080`，`/` 走 history 路由回退到 `index.html`。
