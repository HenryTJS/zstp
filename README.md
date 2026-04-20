# AI智能教学助手工作台

面向学生自学与教师教学管理的 AI 辅助平台，包含前端（学生端/教师端/管理员端）与 Spring Boot 后端。

## 当前项目能力

### 学生端

- 课程广场、课程详情、加入/退出课程
- 知识图谱学习（D3 图谱）与节点详情联动
- 知识点资源查看与学习进度标记
- AI 学习建议、专业关联度分析、AI 助手对话
- AI 练习与测试（含客观题批改）
- 教师发布知识点测试作答
- 组卷、试卷保存与下载（Markdown）
- 错题本、错题重练、学习记录
- 个人资料编辑、修改密码、学习画像（五维能力）
- 个人中心统计项：加入课程数、总学习时长、发布评论数
- 知识点交流区（发帖/回复/点赞）与通知铃铛

### 教师端

- 课程广场、课程详情、课程权限申请
- 课程管理（知识点维护、资料上传/删除、统计看板）
- 发布知识点测试并查看相关统计
- 学情分析：按课程与知识点查看完成率、分数统计、每题得分率、高低得分题
- 学情分析报告：生成 AI 教学建议、逐题分析，支持 Markdown 导出
- 课程简介/封面编辑
- 知识点交流区与通知铃铛
- 个人资料编辑、修改密码、AI 助手

### 管理员端

- 个人中心
- 用户统计（学生/教师）与批量导入（xlsx）
- 公告发布与删除
- 教师课程权限审批通知（顶栏铃铛）

### 后端接口域（主要）

- 用户认证与资料：`/api/users/*`
- 课程与目录：`/api/courses*`
- 知识点与资料：`/api/knowledge-points*`、`/api/materials*`、`/api/resources*`
- AI 能力：`/api/knowledge-graph`、`/api/learning-suggestions`、`/api/major-relevance`、`/api/agent-chat`、`/api/generate-*`、`/api/grade-answer`
- 教师测试与学情分析：`/api/knowledge-point-published-tests*`（含 `/stats`、`/submissions-detail`、`/learning-report`、`/course-summary`）
- 交流区与通知：`/api/knowledge-point-discussions*`（含 `/count-by-user`）、`/api/notifications*`
- 教师权限：`/api/teacher-course-permissions*`、`/api/teacher-course-permission-requests*`
- 公告与健康检查：`/api/announcements*`、`/api/health`

## 技术栈

- 前端：Vue 3、Vite、Vue Router、Axios、D3、ECharts、KaTeX、xlsx
- 后端：Java 17、Spring Boot 3、Spring Data JPA、PostgreSQL
- AI 接入：OpenAI 兼容接口
- 部署：Docker Compose + Nginx

## 仓库结构

```text
zstp/
  backend/               # Spring Boot 后端
  frontend/              # Vue 3 + Vite 前端
  deploy/                # Docker Compose 与部署文档
  start-all.bat          # Windows 一键启动（调用 PowerShell）
  start-all.ps1          # Windows 一键启动脚本
  LICENSE
```

## 本地开发

### 1) 环境要求

- Java 17+
- Maven 3.9+
- Node.js 18+（建议 20）
- PostgreSQL 14+

### 2) 创建数据库

```sql
CREATE DATABASE ai_self_learning;
```

### 3) 配置后端环境变量

后端支持从运行环境变量读取，也会尝试读取 `backend/.env`（`application.yml` 中 `spring.config.import` 已配置为可选）。

常用配置项：

- 数据库：`DATABASE_URL` 或 `POSTGRES_HOST` / `POSTGRES_PORT` / `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD`
- 端口：`PORT`（默认 `5000`）
- AI：`OPENAI_API_KEY`、`OPENAI_BASE_URL`、`OPENAI_MODEL`
- 预置账号：`APP_BOOTSTRAP_USERS_*`
- 上传目录：`UPLOAD_DIR`（默认 `uploads`）

> 请勿提交真实密钥和生产密码。

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

默认地址：`http://localhost:5173`，并通过 Vite 代理将 `/api` 转发到 `http://localhost:5000`。

### 6) Windows 一键启动

在项目根目录执行：

- `start-all.bat`
- `start-all.ps1`

脚本会拉起后端与前端开发服务（新终端）。

## 默认演示账号

由后端启动时的 `app.bootstrap-users` 初始化（可由环境变量覆盖），默认值为：

- 学生：`student_demo / student123`
- 教师：`teacher_demo / teacher123`
- 管理员：`admin_demo / admin123456`

## 认证与安全说明

- 登录接口为 `POST /api/users/login`（identity 支持学工号或邮箱）
- `POST /api/users/register` 当前固定返回 403（禁用自注册）
- 统一修改密码接口：`POST /api/users/change-password`，支持两种模式：
  - 已登录修改：`userId + currentPassword + newPassword`
  - 忘记密码重置：`username + workId + newPassword`
- 前端独立账号安全页路由：`/security`

## 构建

- 前端：`cd frontend && npm run build`
- 后端：`cd backend && mvn package`

## Docker 部署

- 编排文件：`deploy/docker-compose.yml`
- 环境变量模板：`deploy/.env.example`
- 宿主机 Nginx 参考：`deploy/host-nginx.example.conf`
- 详细流程：`deploy/DEPLOY-MAINLAND.md`

## 现状说明

- 前端 `package.json` 当前仅提供 `dev/build/preview`，未内置 lint/test 脚本。
- 后端仓库当前未包含测试用例目录。
- 学生端“总学习时长”已支持**持久化**：前端会话内计时累计，并通过 `POST /api/student-state` 的 `totalLearningSeconds` 写入数据库字段 `student_states.total_learning_seconds`；跨会话可恢复。当前仍未做“多端并发/多标签页合并”的严格去重。

## 许可证

本项目遵循 Apache License 2.0，详见 `LICENSE`。