# MindNest（智巢）· AI 智能笔记与知识库系统

面向个人学习者与内容创作者的全栈在线笔记系统：知识库管理、富文本笔记、AI 辅助创作（大纲/润色/摘要/问答）、文件上传、公开分享、数据统计与笔记导出。

> Java 全栈练习项目：完整覆盖「需求 → 设计 → 开发 → 联调 → 部署」全流程。

## 特性一览

- 🔐 用户体系：注册 / 登录 / JWT 认证 / 个人中心（资料、密码）
- 📚 知识库：增删改查、公开/私有切换、无登录公开分享页
- 📝 笔记：富文本编辑（WangEditor）、分类、标签、回收站、关键词搜索、浏览计数
- 🤖 AI 能力：大纲生成、内容润色、摘要生成、基于笔记问答（OpenAI 兼容接口，mock 兜底）
- 🖼️ 文件：上传（本地磁盘）、绑定笔记、详情回显
- 📊 运营：操作日志（AOP）、数据统计面板
- 📤 导出：笔记导出 Markdown / PDF

## 技术栈

| 端 | 技术 |
| :--- | :--- |
| 后端 | Spring Boot 3.2 / Spring Security + JWT / MyBatis-Plus / MySQL 8 / Hutool / SpringDoc OpenAPI / AOP |
| 前端 | Vue 3 / Vite / WangEditor / Pinia / Vue Router / Axios |
| AI | OpenAI 兼容接口（ChatAnywhere 聚合，`gpt-4o-mini`） |
| 部署 | Docker / Docker Compose / Nginx |

## 目录结构

```
ai-note-system/
├── backend/              # Spring Boot 后端
│   ├── src/main/java/com/ainote/
│   │   ├── common/        # 统一返回、全局异常、配置(JWT/Security/MyBatis/Jackson)、工具
│   │   └── module/        # user / kb / category / tag / note / file / ai / log / stats / export / publicview
│   ├── src/main/resources/application.yml
│   └── src/main/resources/sql/init.sql   # 建库建表脚本
├── frontend/             # Vue3 前端
│   ├── src/api / store / router / views
│   └── nginx.conf        # 生产反向代理与 history 路由
├── docker-compose.yml    # 一键编排 MySQL+Redis+后端+前端
├── .env.example          # 环境变量示例（AI/JWT）
└── README.md
```

## 一、本地开发

### 环境要求
- JDK 21、Maven 3.9+
- Node 18+、npm
- MySQL 8

### 1. 初始化数据库
```bash
# 在 MySQL 中执行建表脚本（默认库 ai_note）
mysql -uroot -p < backend/src/main/resources/sql/init.sql
```

### 2. 启动后端
```bash
cd backend
mvn -s .mvn/settings.xml spring-boot:run
# 接口文档: http://localhost:8080/api/swagger-ui.html
```
连接参数可在 `backend/src/main/resources/application.yml` 中调整，或通过环境变量覆盖（见下方「环境变量」）。

### 3. 启动前端
```bash
cd frontend
npm install
npm run dev        # http://localhost:5173（占用时自动用 5174）
```
Vite 已将 `/api` 代理到后端 `8080`，无需跨域配置。

### 联调账号
`tester / 123456`（已在建库后注册）。

## 二、Docker 一键部署

```bash
# 1. 复制并编辑环境变量（AI Key、JWT 密钥）
cp .env.example .env

# 2. 启动全部服务（MySQL + 后端 + 前端）
docker compose up -d --build
```

| 服务 | 端口 | 说明 |
| :--- | :--- | :--- |
| 前端 web | 80 | 访问入口 `http://localhost/`（Nginx 反代 API） |
| 后端 backend | 8080 | Swagger `http://localhost:8080/api/swagger-ui.html` |
| MySQL | 3306 | 数据卷 mysql-data，首次启动自动执行 init.sql |

常用命令：`docker compose logs -f` 查看日志、`docker compose down` 停止、`docker compose ps` 查看状态。

> 依赖从构建镜像开始自动拉取，首次构建较慢属正常。

## 三、环境变量（可覆盖默认值）

| 变量 | 默认 | 说明 |
| :--- | :--- | :--- |
| `MYSQL_HOST / MYSQL_PORT / MYSQL_USER / MYSQL_PASSWORD` | `localhost:3307 root root123456` | 数据库连接 |
| `JWT_SECRET` | 内置开发密钥 | **生产务必改为随机强密钥** |
| `AI_BASE_URL` | `https://api.chatanywhere.tech/v1` | OpenAI 兼容地址 |
| `AI_API_KEY` | 空 | ChatAnywhere API Key |
| `AI_MODEL` | `gpt-4o-mini` | 模型名 |
| `AI_MOCK_ENABLED` | `true` | `true` 返回模拟结果；填 Key 后设 `false` 走真实模型 |
| `FILE_UPLOAD_DIR` | `./upload` | 文件本地存储目录（Docker 内 `/app/upload` 挂载数据卷） |

## 四、核心接口

统一返回格式 `{ code, message, data }`；除标 * 外均需 `Authorization: Bearer <token>`。

| 模块 | 方法与路径 |
| :--- | :--- |
| 认证 | `POST /api/auth/register` `POST /api/auth/login` |
| 用户 | `GET /api/user/info` `PUT /api/user/update` `PUT /api/user/password` |
| 知识库 | `GET/POST/PUT/DELETE /api/kb/*` |
| 分类 | `GET/POST/PUT/DELETE /api/category/*` |
| 标签 | `GET/POST/DELETE /api/tag/*` |
| 笔记 | `GET/POST/PUT /api/note/*` `DELETE /api/note/delete/{id}` `PUT /api/note/restore/{id}` `GET /api/note/recycle` |
| AI | `POST /api/ai/generate-outline` `POST /api/ai/polish` `POST /api/ai/summarize` `POST /api/ai/chat` |
| 文件 | `POST /api/file/upload` `GET /api/file/list` `DELETE /api/file/delete/{id}` `GET /api/files/**`* |
| 导出 | `GET /api/export/markdown/{noteId}` |
| 统计 | `GET /api/stats/overview` |
| 日志 | `GET /api/log/list` |
| 公开分享 | `GET /api/public/kb/{id}` `GET /api/public/note/{id}`（免登录）* |

完整接口见 Swagger：`/api/swagger-ui.html`。

## 开发规范
见 [backend/DEVELOPMENT.md](backend/DEVELOPMENT.md)：统一返回、全局异常、RESTful 路径、JWT 鉴权、逻辑删除、命名与提交规范。
