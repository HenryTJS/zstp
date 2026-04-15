# AI 学生自学平台

面向学生自学与教师教学管理的 AI 辅助平台。  
项目包含**学生端、教师端、管理员端**与后端服务，支持知识图谱学习、知识点交流、站内通知、学习资源进度、教师发布测试、AI 出题与批改、组卷与试卷存档、资料管理、教师课程权限申请与审批等能力。

## 功能总览

### 学生端

- **顶栏**：课程搜索、消息铃铛、AI 学习助手悬浮按钮
- **课程广场**：浏览课程、加入/退出课程、查看任课教师；未加入课程时可预览详情
- **课程详情**：课程元信息、封面等
- **知识图谱**：交互式图谱、学习建议、专业关联度、按知识点查看资料与**学习资源**及完成标记
- **知识点交流区**：发帖、回复、点赞；根帖可选 **正常帖** / **答疑帖**
- **消息通知**：答疑帖、讨论帖、回复、点赞等；可深链到图谱对应知识点与交流帖
- **AI 练习**：单题/套题生成、练习与测试模式、客观题 AI 批改
- **教师发布测试**：在图谱上下文中作答教师发布的知识点测试
- **组卷与试卷**：从知识点组卷、保存试卷列表；下载以 **Markdown** 为主
- **错题与记录**：错题本、学习记录、已保存试卷、错题重练
- **个人中心**：资料编辑、修改密码、学习画像与**五维能力雷达**

### 教师端

- **顶栏**：与学生端类似的课程搜索；消息铃铛；AI 学习助手
- **课程广场**：浏览目录、查看是否已授权；对未授权课程可提交 **课程权限申请**
- **课程详情**：与权限、申请状态相关的信息展示
- **课程管理**：知识点树维护、教学资料上传与按知识点查看/删除、学生维度进度看板、**发布知识点测试**与统计等
- **知识点交流区**：根帖可选 **正常帖** / **讨论帖**
- **消息通知**：讨论帖、回复、点赞等；可跳转至课程管理并打开对应知识点交流区
- **公告**、**个人中心**

### 管理员端

- **导航页**：个人中心、用户统计与批量导入、公告管理、课程权重与学分配置
- **顶栏「教师权限申请」铃铛**：查看待审批列表；**同意/不同意**均需填写理由。同意 **新增课程** 时，后端会写入课程目录并为申请教师授权；**加入已有** 则仅授权。日常以教师在课程广场提交申请 + 管理员在此审批为主。

### 账号与安全

- 统一登录；`POST /api/users/register` 固定返回 **403**
- 密码修改与资料更新在平台内提供；独立账号安全页路由为 `/security`

## 知识点交流区与消息通知（摘要）


| 根帖类型 | 发帖角色   | 通知对象                          |
| ---- | ------ | ----------------------------- |
| 正常帖  | 学生/教师  | 无群发，仅互动见下                     |
| 答疑帖  | 学生     | 拥有该课程权限的教师                    |
| 讨论帖  | 教师/管理员 | `joined_courses_json` 中含该课的学生 |


**互动通知**：你的帖子或回复被 **回复** 或 **点赞** 时，会收到通知并可从铃铛跳转。

**前端跳转**：学生使用路由查询参数 `dc`（课程名）、`dp`（知识点名）、`dpost`（帖子 id，可选），例如 `/student/graph?dc=...&dp=...&dpost=...`；教师为 `/teacher/manage?dc=...&dp=...&dpost=...`。处理完深链后查询参数会被清除。

**后端注意**：项目关闭 `spring.jpa.open-in-view`；交流区列表接口使用只读事务组装帖子树，避免懒加载异常。若旧库缺少 `post_kind` 列，由 Hibernate `ddl-auto: update` 迁移，旧行可为空并视为 `NORMAL`。

## 技术栈

- 前端：`Vue 3`、`Vite`、`Vue Router`、`Axios`、`ECharts`、`D3`、`KaTeX`、`xlsx`
- 后端：`Java 17`、`Spring Boot 3`、`Spring Data JPA`、`PostgreSQL`
- AI 接入：OpenAI 兼容接口
- 部署：`Docker Compose` + `Nginx`

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

### 前端架构（已模块化）

前端采用「按角色 + 按业务域」的模块化组织，入口组件尽量只负责页面编排，业务状态与副作用下沉到 `composables`，样式集中到 `src/styles`。

```text
frontend/src/
  modules/
    student/
      StudentPortal.vue
      course/
        components/
        composables/
        index.js
      graph/
        components/
        composables/
        index.js
      learning/
        components/
        composables/
        index.js
      profile/
        components/
        composables/
        index.js
      review/
        components/
        composables/
        index.js
    teacher/
      TeacherPortal.vue
      pages/
      components/
      composables/
      index.js
    admin/
      AdminPortal.vue
      components/
      index.js
  styles/
    student/
    teacher/
    admin/
  router/
    index.js
  App.vue
```

### 前端拆分约定

- 页面级组件：负责模板编排与事件分发，避免堆积业务逻辑。
- 业务逻辑：统一收敛到 `composables`（接口调用、状态管理、生命周期、路由联动）。
- 样式：优先放在 `src/styles/<role>/`，组件中通过 `@import` 或 `style src` 引入。
- 模块出口：每个业务域使用 `index.js` 暴露统一入口，减少跨目录耦合。
- 路由：统一在 `router/index.js` 维护，角色门户路由直接指向 `modules` 下入口组件。

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

后端会从 `backend/.env` 读取配置。常用配置项：

- 数据库：`DATABASE_URL` 或 `POSTGRES_HOST` / `POSTGRES_PORT` / `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD`
- 服务端口：`PORT` 或 `SERVER_PORT`
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
- 管理员：`admin_demo / admin123456`

生产环境请务必修改默认密码并替换为正式账号体系。

## API 概览

以下为常用接口分组（完整参数与返回请以后端控制器为准）：

- 用户与认证
  - `POST /api/users/login`
  - `POST /api/users/change-password`
  - `POST /api/users/update`
  - `GET /api/users`（可选 `?role=student|teacher`；其它值则返回全部用户）
  - `POST /api/users/bulk-import`
  - `POST /api/users/register`（固定禁用，返回 403）
- 学生状态（学习记录、错题、加入课程等）
  - `GET /api/student-state?userId=`
  - `POST /api/student-state`
- 学生维度分（雷达图等）
  - `GET /api/student-dimension-scores?userId=`（可选 `course`）
- 课程与专业
  - `GET /api/courses?majorCode=`（课程名列表）
  - `GET /api/courses/catalog?userId=`
  - `GET /api/courses/detail?courseName=&userId=`
  - `PUT /api/courses/meta`
  - `POST /api/courses/cover/upload`
  - `GET /api/majors/tree`
- 课程配置（五维权重、学分规则）
  - `GET /api/course-configs?adminUserId=`
  - `GET /api/course-configs/{courseName}?adminUserId=`
  - `PUT /api/course-configs/{courseName}`
- 教师课程权限
  - `GET /api/teacher-course-permissions?teacherId=`
  - `POST /api/teacher-course-permissions/teachers-for-courses`
- 教师课程权限申请
  - `GET /api/teacher-course-permission-requests`
  - `POST /api/teacher-course-permission-requests`
  - `POST /api/teacher-course-permission-requests/decide`
- 学习资源
  - `GET /api/resources/by-knowledge-point`
  - `POST /api/resources/complete`
  - `GET /api/resources/progress`
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
  - `GET /api/exams/{id}/download`（`type`：`md`、`md_answer`、`paper`、`answer` 等，视库中是否存有对应内容）
  - `POST /api/exams/{id}/render`
  - `DELETE /api/exams/{id}`
- 教师发布知识点测试
  - `POST /api/knowledge-point-published-tests` 及 `GET`/`POST` 等子路径（见 `KnowledgePointPublishedTestController`）
- 教学数据
  - `GET/POST /api/knowledge-points`
  - `GET /api/materials`、`GET /api/materials/by-knowledge-point`、`GET /api/materials/{id}/download`
  - `POST /api/materials/upload`
  - `DELETE /api/materials/{id}`
- 公告
  - `GET /api/announcements`
  - `POST /api/announcements`
- **知识点交流区**
  - `GET /api/knowledge-point-discussions?courseName=&pointName=&userId=`
  - `POST /api/knowledge-point-discussions`（body 含 `postKind`：`NORMAL` | `QA` | `DISCUSSION`，根帖使用；回复可省略）
  - `POST /api/knowledge-point-discussions/{postId}/like`
  - `DELETE /api/knowledge-point-discussions/{postId}`（见具体控制器与权限校验）
- **站内通知**
  - `GET /api/notifications?userId=&limit=`
  - `POST /api/notifications/{id}/read?userId=`
  - `POST /api/notifications/read-all?userId=`
  - `DELETE /api/notifications/{id}?userId=`
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

## 部署

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
- 交流区「加载失败」：若曾关闭 `open-in-view`，需保证后端为当前版本；查看浏览器网络请求状态码与响应体。
- 服务器 HTTPS 不生效：按 `deploy/DEPLOY-MAINLAND.md` 检查域名解析、备案状态和证书签发。

## 贡献

欢迎提交 Issue 或 Pull Request。请在变更说明中包含：

- 变更目标
- 影响范围
- 本地验证步骤

## 许可证

本项目遵循 Apache License 2.0，详见根目录 `LICENSE`。