# 后端开发规范

> 本文件是所有后端开发人员必须遵守的强制约定。请先阅读再动手，避免返工。

## 1. 模块与分层约定

每个业务模块在 `src/main/java/com/ainote/module/<模块>/` 下按以下子包组织：

```
module/user
├── controller/     # 接口层：接收请求、参数校验、返回 Result
├── service/        # 业务接口
│   └── impl/       # 业务实现
├── mapper/         # MyBatis-Plus Mapper（@MapperScan 已自动扫描 **.mapper）
├── entity/         # 数据库实体（字段与表结构一一对应）
├── dto/            # 入参对象（建议用 jakarta.validation 注解校验）
└── vo/             # 出参对象（不暴露实体敏感字段）
```

依赖规范：
- **Controller 不得直接操作数据库**，必须通过 service。
- **实体（entity）不得作为接口出参直接返回**，应转成 vo。
- 通用能力放 `common/`，业务跨模块依赖放到对应 service，禁止 controller 间互相调用。

## 2. 命名规范

| 场景 | 规则 | 示例 |
| :--- | :--- | :--- |
| 包名 | 全小写，反域名 | `com.ainote.module.note` |
| 类名 | 大驼峰 | `NoteController` |
| 方法/字段 | 小驼峰 | `getNoteList` |
| 常量 | 全大写+下划线 | `MAX_TITLE_LEN` |
| 数据库表 | 小写下划线 + 单数 | `note`、`knowledge_base` |
| 数据库字段 | 小写下划线 | `create_time` |
| 接口路径 | 小写 + `/api/<模块>` | `/api/note/list` |
| Service 接口名 | `XxxService` | `NoteService` |
| Service 实现名 | `XxxServiceImpl` | `NoteServiceImpl` |
| Controller 名 | `XxxController` | `NoteController` |

## 3. 接口规范

### 3.1 风格
- RESTful：`GET` 查询、`POST` 新增、`PUT` 更新、`DELETE` 删除。
- `context-path=/api` 已统一预置前缀，**控制器路径不再写 `/api`**；完整 URL = `/api` + 控制器路径（如登录为 `POST /api/auth/login`）。
- 公开接口（如 `/auth/**`）需在 `SecurityConfig.PUBLIC_URIS` 中显式放行。

### 3.2 统一返回
所有接口一律返回 `Result<T>`（`code`/`message`/`data`）：
- 成功：`Result.success(data)`
- 业务失败：抛 `BusinessException`，由全局异常处理器统一转为 Result。

```java
@Operation(summary = "新建笔记")
@PostMapping("/add")
public Result<Long> add(@Valid @RequestBody NoteAddDTO dto) {
    return Result.success(noteService.add(dto));
}
```

### 3.3 鉴权
- 请求头携带 `Authorization: Bearer <token>`。
- 需要登录的接口不要自己校验，通过 `SecurityUtil.getUserId()` 获取当前用户ID；未登录由安全框架返回 401。

### 3.4 参数校验
- 入参 DTO 使用 `jakarta.validation` 注解（`@NotBlank`、`@Size` 等），Controller 方法加 `@Valid`。

### 3.5 幂等与语义
- 新增用 `POST`，对可重复提交需做幂等处理；删除用 `DELETE`（逻辑删除，非物理删除）。

## 4. 数据库规范

- 字符集统一 `utf8mb4`；所有主键 `bigint` 雪花（`@TableId(type = IdType.ASSIGN_ID)`）。
- 必须包含逻辑删除字段 `deleted`（`@TableLogic`），**禁止物理删除核心业务数据**。
- 时间字段 `create_time` / `update_time` 由数据库默认值维护，MyBatis 不参与写入。
- 布尔/状态用 `TINYINT`（0/1），禁止用 `0/1` 之外的魔法值，含义写注释。
- 表结构调整需同步更新 `sql/init.sql` 并追加增量脚本，禁止只改代码。

## 5. 异常与日志

- 业务可预期错误抛 `BusinessException`（带友好提示），避免直接抛运行时异常给前端。
- 切勿向上层抛 `Exception` 而不做处理；全局兜底会记录 `ERROR` 日志。
- 生产环境将 `application.yml` 中 `log-impl` 改为 `org.apache.ibatis.logging.slf4j.Slf4jImpl`，避免打印 SQL。

## 6. 配置与密钥管理

- 敏感配置一律使用环境变量占位：`${MYSQL_PASSWORD:root}`、`${JWT_SECRET:...}`、`${AI_API_KEY:...}`。
- **严禁把真实密码 / API Key 提交到 git**（已通过 `.gitignore` 忽略 `application-local.yml`）。
- 本地私密配置放 `application-local.yml`，以 `--spring.profiles.active=dev,local` 启动。

## 7. 构建与运行

```bash
# 编译
mvn -s .mvn/settings.xml compile
# 启动
mvn -s .mvn/settings.xml spring-boot:run
# 接口文档
http://localhost:8080/api/swagger-ui.html
```

> `-s .mvn/settings.xml` 将 Maven 本地仓库重定向到 `../.m2_repo`（工作区内），规避沙箱对系统目录写入的限制。若本机需默认仓库，可用 `settings.xml` 内配置路径替换。

## 8. Git 规范

- 分支：`main`（保护，只接受 merge），功能分支 `feat/<功能名>`，Bug 修复 `fix/<bug>`。
- Commit 信息格式：`<type>(<scope>): <subject>`。
  - `feat` 新功能 / `fix` 修复 / `docs` 文档 / `refactor` 重构 / `style` 格式 / `test` 测试 / `chore` 杂项
  - 示例：`feat(note): add note recycle bin`
- 一个提交只做一件事；不允许提交 `target/`、`.idea/`、`*.log`（已在 `.gitignore`）。

## 9. 常见约定速查

| 事项 | 约定 |
| :--- | :--- |
| 返回码 | `200`成功 `400`参数错误 `401`未登录 `403`无权限 `404`不存在 `500`系统错误 `600`业务错误 |
| 分页 | 使用 MyBatis-Plus `Page` + `PaginationInnerInterceptor` |
| 逻辑删除 | entity 字段加 `@TableLogic`，无需手动写删除条件 |
| 主键 | 雪花 `ASSIGN_ID` |
| API 分组 | `@Tag(name = "模块名")`，每个方法 `@Operation(summary = "...")` |