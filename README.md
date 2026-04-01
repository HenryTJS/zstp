# AI 学生自学平台

一个面向学生自学场景的教学辅助平台原型，前端使用 Vue 3 + Vite，后端使用 Spring Boot（Java），数据存储为 PostgreSQL。功能包括知识图谱可视化、AI 自动出题/组卷、AI 批改与教师资料上传。

## 主要特性

- 学生端：知识图谱、题目生成、在线练习与 AI 批改
- 教师端：资料上传、资料列表管理
- 统一登录、关闭注册、支持修改密码

## 仓库结构（简要）

```text
e:/zstp/
  backend/        # Spring Boot 后端代码及配置
  frontend/       # Vue 3 + Vite 前端代码
  start-all.bat   # Windows 一键启动脚本
  start-all.ps1   # PowerShell 一键启动脚本
  uploads/        # 临时/演示文件上传目录
```

## 快速开始（开发环境）

先决条件：

- Java 17 或更高
- Maven 3.x
- Node.js + npm
- PostgreSQL

1) 启动 PostgreSQL 并创建数据库：

```sql
CREATE DATABASE ai_self_learning;
```

2) 后端（开发）

```bash
cd backend
# 使用 Maven 直接运行
mvn spring-boot:run

# 或打包后运行
mvn package
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

后端默认监听地址：`http://localhost:5000`（如需改变端口，请查看 `backend/src/main/resources/application.yml`）。

环境变量：请参考 `backend/.env.example`，常见项包括数据库连接、`OPENAI_API_KEY`（若使用 OpenAI 兼容服务）、以及用于引导预置账号的 `APP_BOOTSTRAP_USERS_*` 配置。

3) 前端（开发）

```bash
cd frontend
npm install
npm run dev
```

前端默认开发服务：`http://localhost:5173`，项目使用 Vite 的 `/api` 代理转发到后端接口。

注：前端脚本请参见 `frontend/package.json`，开发命令为 `npm run dev`，构建命令为 `npm run build`。

4) 一键启动（Windows）

项目根目录包含：

- `start-all.bat`（双击运行）
- `start-all.ps1`（在 PowerShell 中运行）

脚本会在各自终端中启动后端与前端（并在需要时复制 `.env.example` 到 `.env`）。

## 默认账号（仅用于本地演示）

- 学生：`student_demo / student123`
- 教师：`teacher_demo / teacher123`

如需修改，请编辑 `backend/.env.example` 中的引导配置并复制到 `backend/.env`。

## 主要接口（示例）

- `POST /api/users/login` — 登录
- `POST /api/users/change-password` — 修改密码
- `GET /api/users?role=student|teacher` — 获取用户列表
- `POST /api/materials/upload` — 教师上传资料（multipart/form-data）
- `GET /api/materials` — 获取资料列表
- `POST /api/knowledge-graph` — 生成知识图谱
- `POST /api/generate-question` — 生成题目与解析
- `POST /api/grade-answer` — AI 批改
- `POST /api/generate-test` — AI 组卷

（更多接口与参数请参阅后端控制器实现于 `backend/src/main/java/com/teacher/backend/controller`）

## 架构与技术栈

- 前端：Vue 3 + Vite，依赖包括 `axios`, `d3`, `echarts`, `katex`。
- 后端：Spring Boot + Spring Data JPA，使用 PostgreSQL 作为持久化储存。

## 构建与部署建议

- 后端：`mvn package` 打包为可执行 jar，生产环境使用进程管理器（systemd、pm2、Windows 服务等）运行。
- 前端：`npm run build` 后将 `dist/` 部署到静态服务器（nginx、静态托管）。

## 本地调试提示

- 若后端未能连接数据库，检查 `backend/.env` 或 `application.yml` 中的 `DATABASE_URL`/JDBC 配置。
- 若需要测试 OpenAI 功能，请在环境变量中设置 `OPENAI_API_KEY` 或修改为兼容的 API 地址。

## 贡献与开发流程

- 欢迎提交 Issue 或 Pull Request。请在 PR 中说明变更目的、影响范围以及运行验证步骤。

## 许可证

该项目遵循仓库根目录的 LICENSE 文件。
