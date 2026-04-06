# AI 学生自学平台

面向学生自学与教师教学管理的 AI 辅助平台。  
项目包含**学生端、教师端、管理员端**与后端服务，支持知识图谱学习、知识点交流、站内消息通知、AI 出题与解析、组卷、资料管理、课程权限与申请等能力。

## 功能总览

### 学生端

- 课程广场：浏览课程、加入/退出课程、查看任课教师
- 知识图谱：交互式图谱、学习建议、专业关联度、按知识点资料下载
- **知识点交流区**：发帖、回复、点赞；根帖可选 **正常帖** / **答疑帖**（通知有该课权限的教师）
- **消息通知**：顶栏铃铛；答疑、讨论、回复、点赞等通知可点击跳转到图谱对应知识点与交流帖（需已加入课程）
- AI 练习：单题/套题生成、测试与考试模式、客观题批改
- 错题与学习记录、已保存试卷、公告
- 个人中心：资料编辑、修改密码、学习统计

### 教师端

- 课程广场与市场、**课程权限申请**（管理员审批）
- 知识点管理：增删改、层级、Markdown 导入
- 教学资料：上传、按知识点查看与删除
- **知识点交流区**（弹窗）：根帖可选 **正常帖** / **讨论帖**（通知已加入该课的学生）
- **消息通知**：顶栏铃铛；讨论帖、回复、点赞等可跳转至「课程管理」并打开对应知识点交流区（需有该课权限）
- 公告（只读）、个人中心

### 管理员端

- 用户与批量导入、公告管理、**教师课程权限**分配
- 课程目录维护、教师权限申请审批

### 账号与安全

- 统一登录；注册接口默认关闭（依赖预置或导入账号）
- 密码修改与账号信息更新

## 知识点交流区与消息通知（摘要）

| 根帖类型 | 发帖角色 | 通知对象 |
|---------|---------|---------|
| 正常帖 | 学生/教师 | 无群发，仅互动见下 |
| 答疑帖 | 学生 | 拥有该课程权限的教师 |
| 讨论帖 | 教师/管理员 | `joined_courses_json` 中含该课的学生 |

**互动通知（与帖子类型无关）**：你的帖子或回复被 **回复** 或 **点赞** 时，会收到通知并可从铃铛跳转。

**前端跳转**：学生使用路由查询参数 `dc`（课程名）、`dp`（知识点名）、`dpost`（帖子 id，可选），例如 `/student/graph?dc=...&dp=...&dpost=...`；教师为 `/teacher/manage?dc=...&dp=...&dpost=...`。处理完深链后查询参数会被清除。

**后端注意**：项目关闭 `spring.jpa.open-in-view`；交流区列表接口使用只读事务组装帖子树，避免懒加载异常。若旧库缺少 `post_kind` 列，由 Hibernate `ddl-auto: update` 迁移，旧行可为空并视为 `NORMAL`。

## 技术栈

- 前端：`Vue 3`、`Vite`、`Vue Router`、`Axios`、`ECharts`、`D3`、`KaTeX`
- 后端：`Java 17`、`Spring Boot 3`、`Spring Data JPA`、`PostgreSQL`
- AI 接入：OpenAI 兼容接口（可配置 `OPENAI_BASE_URL` / `OPENAI_MODEL`）
- 部署：`Docker Compose` + `Nginx`（HTTPS 反向代理）

## 仓库结构

```text
zstp/
  backend/               # Spring Boot 后端
  frontend/              # Vue 3 + Vite 前端
  deploy/                # Docker Compose 与大陆部署文档
  start-all.bat          # Windows 一键启动（CMD）
  start-all.ps1          # Windows 一键启动（PowerShell）
  LICENSE
```

## 快速开始（本地开发）

### 1) 环境要求

- Java 17+
- Maven 3.x
- Node.js 18+ 与 npm
- PostgreSQL 14+

### 2) 准备数据库

```sql
CREATE DATABASE ai_self_learning;
```

### 3) 配置后端环境变量

后端会从 `backend/.env` 读取配置（由 `application.yml` 导入）。常用配置项：

- 数据库：`DATABASE_URL` 或 `POSTGRES_HOST` / `POSTGRES_PORT` / `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD`
- 服务端口：`SERVER_PORT`（默认 `5000`）
- AI：`OPENAI_API_KEY`、`OPENAI_BASE_URL`、`OPENAI_MODEL`
- 预置账号：`APP_BOOTSTRAP_USERS_*`

> 注意：请勿提交真实密钥、真实数据库密码等敏感信息到仓库。

### 4) 启动后端

```bash
cd backend
mvn spring-boot:run
```

或：

```bash
cd backend
mvn package
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

默认地址：`http://localhost:5000`

### 5) 启动前端

```bash
cd frontend
npm install
npm run dev
```

默认地址：`http://localhost:5173`，并通过 Vite 代理将 `/api` 转发到后端。

### 6) Windows 一键启动

在项目根目录执行：

- `start-all.bat`
- `start-all.ps1`

脚本会启动后端和前端开发服务。

## 默认演示账号

- 学生：`student_demo / student123`
- 教师：`teacher_demo / teacher123`
- 管理员：`admin_demo / admin123456`（见 `application.yml` 中 `app.bootstrap-users`）

生产环境请务必修改默认密码并替换为正式账号体系。

## API 概览

以下为常用接口分组（完整参数与返回请以后端控制器为准）：

- 用户与认证
  - `POST /api/users/login`
  - `POST /api/users/change-password`
  - `POST /api/users/update`
  - `GET /api/users?role=student|teacher`
  - `POST /api/users/bulk-import`
  - `POST /api/users/register`（默认禁用）
- 学生状态（学习记录、错题、加入课程等）
  - `GET /api/student-state?userId=`
  - `POST /api/student-state`
- 课程与专业
  - `GET /api/courses?majorCode=`
  - `POST /api/courses`（管理员）
  - `GET /api/majors/tree`
- 教师课程权限
  - `GET /api/teacher-course-permissions?teacherId=`
  - `POST /api/teacher-course-permissions/assign`
  - `POST /api/teacher-course-permissions/teachers-for-courses`
- 教师课程权限申请
  - `GET /api/teacher-course-permission-requests`
  - `POST /api/teacher-course-permission-requests`
  - `POST /api/teacher-course-permission-requests/decide`
- AI 与学习
  - `POST /api/knowledge-graph`
  - `POST /api/learning-suggestions`
  - `POST /api/major-relevance`
  - `POST /api/agent-chat`
  - `POST /api/generate-question`
  - `POST /api/generate-questions`
  - `POST /api/grade-answer`
  - `POST /api/exams/save`
  - `GET /api/exams`
  - `GET /api/exams/{id}/download`
  - `POST /api/exams/{id}/render`
- 教学数据
  - `GET/POST /api/knowledge-points`
  - `GET /api/materials`、`GET /api/materials/by-knowledge-point`
  - `POST /api/materials/upload`
  - `DELETE /api/materials/{id}`（见具体控制器）
- 公告
  - `GET /api/announcements`
  - `POST /api/announcements`
- **知识点交流区**
  - `GET /api/knowledge-point-discussions?courseName=&pointName=&userId=`
  - `POST /api/knowledge-point-discussions`（body 含 `postKind`：`NORMAL` | `QA` | `DISCUSSION`，根帖使用；回复可省略）
  - `POST /api/knowledge-point-discussions/{postId}/like`
- **站内通知**
  - `GET /api/notifications?userId=&limit=`
  - `POST /api/notifications/{id}/read?userId=`
- 健康检查
  - `GET /api/health`

## 构建与发布

- 前端构建：
  ```bash
  cd frontend
  npm run build
  ```
- 后端构建：
  ```bash
  cd backend
  mvn package
  ```

## 部署（中国大陆服务器）

项目提供完整大陆部署文档，包含腾讯云服务器、域名解析、备案、HTTPS 证书、Nginx 反代与 Docker Compose 部署流程：

- 详细步骤：`deploy/DEPLOY-MAINLAND.md`
- 编排文件：`deploy/docker-compose.yml`
- 环境模板：`deploy/.env.example`
- 宿主机 Nginx 参考：`deploy/host-nginx.example.conf`

## 质量保障现状

- 当前仓库未提供统一的前端 `lint/test` 脚本。
- 当前仓库未提供后端测试目录与自动化测试用例。

如需用于生产，建议补充：

- 前端 ESLint + 单元测试
- 后端集成测试与关键接口回归测试
- CI（构建、测试、镜像发布）

## 常见问题（FAQ）

- 后端连不上数据库：优先检查 `backend/.env` 中数据库配置与 PostgreSQL 是否可访问。
- AI 功能不可用：检查 `OPENAI_API_KEY`、`OPENAI_BASE_URL`、`OPENAI_MODEL`。
- 前端接口报错：确认后端已启动在 `5000` 端口，且前端代理配置未修改。
- 交流区「加载失败」：若曾关闭 `open-in-view`，需保证后端为当前版本（列表接口带只读事务）；查看浏览器网络请求状态码与响应体。
- 服务器 HTTPS 不生效：按 `deploy/DEPLOY-MAINLAND.md` 检查域名解析、备案状态和证书签发。

## 贡献

欢迎提交 Issue 或 Pull Request。请在变更说明中包含：

- 变更目标
- 影响范围
- 本地验证步骤

## 许可证

本项目遵循 Apache License 2.0，详见根目录 `LICENSE`。
