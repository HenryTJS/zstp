# AI 学生自学平台

面向学生自学与教师教学管理的 AI 辅助平台。  
项目包含学生端、教师端和后端服务，支持知识图谱学习、AI 出题与解析、组卷、资料管理等能力。

## 功能总览

- 学生端
  - 知识图谱查看与学习建议
  - AI 测试生成、AI 组卷
  - 客观题批改与错题记录
  - 学习状态记录、个人资料与密码修改
- 教师端
  - 知识点管理（新增、编辑、删除与层级）
  - 教学资料上传、列表查看、按知识点筛选与删除
  - Markdown 导入知识点
- 账号体系
  - 统一登录
  - 注册接口已禁用（默认依赖预置账号）

## 技术栈

- 前端：`Vue 3`、`Vite`、`Vue Router`、`Axios`、`ECharts`、`D3`、`KaTeX`
- 后端：`Java 17`、`Spring Boot 3`、`Spring Data JPA`、`PostgreSQL`
- AI 接入：OpenAI 兼容接口（可配置 `OPENAI_BASE_URL` / `OPENAI_MODEL`）
- 部署：`Docker Compose` + `Nginx`（HTTPS 反向代理）

## 仓库结构

```text
e:/zstp/
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

生产环境请务必修改默认密码并替换为正式账号体系。

## API 概览

以下为常用接口分组（完整参数与返回请以后端控制器实现为准）：

- 用户与认证
  - `POST /api/users/login`
  - `POST /api/users/change-password`
  - `PUT /api/users/{id}`
  - `GET /api/users?role=student|teacher`
  - `POST /api/users/register`（已禁用，返回 403）
- AI 与学习
  - `POST /api/knowledge-graph`
  - `POST /api/learning-advice`
  - `POST /api/generate-question`
  - `POST /api/grade-answer`
  - `POST /api/generate-test`
  - `POST /api/grade-subjective`
  - `POST /api/exam-papers/save`
  - `GET /api/exam-papers/{id}/download-markdown`
- 教学数据
  - `GET/POST/PUT/DELETE /api/knowledge-points`
  - `POST /api/materials/upload`
  - `GET /api/materials`
  - `DELETE /api/materials/{id}`
  - `GET /api/student-states/{studentId}`
  - `POST /api/student-states`
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
- 服务器 HTTPS 不生效：按 `deploy/DEPLOY-MAINLAND.md` 检查域名解析、备案状态和证书签发。

## 贡献

欢迎提交 Issue 或 Pull Request。请在变更说明中包含：

- 变更目标
- 影响范围
- 本地验证步骤

## 许可证

本项目遵循 Apache License 2.0，详见根目录 `LICENSE`。
