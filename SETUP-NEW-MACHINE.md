# 新电脑本地环境配置指南

> 适用于从旧电脑拷贝项目后，在新电脑上配置本地运行环境（Windows）。

## 1. 需要安装的工具（按顺序）

> 说明：下表中「免登录直链」均可直接下载，无需注册/登录。

| 序号 | 工具                             | 版本要求                                      | 官网下载（多数免登录）                                                                                                      | 免登录国内镜像                                                                                                     |
| ---- | -------------------------------- | --------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| 1    | Java (JDK)                       | 17+（推荐**Temurin 21 LTS** 或 17 LTS） | Adoptium：https://adoptium.net/temurin/releases/?version=21 （免登录直下）                                                  | 清华 TUNA：https://mirrors.tuna.tsinghua.edu.cn/Adoptium/ （免登录直下）                                           |
| 2    | Apache Maven                     | 3.9+                                          | https://maven.apache.org/download.cgi （免登录直下）                                                                        | 清华 TUNA：https://mirrors.tuna.tsinghua.edu.cn/apache/maven/maven-3/ （免登录直下）                               |
| 3    | Node.js                          | 18+（推荐 20 LTS）                            | https://nodejs.org/zh-cn/download （免登录直下）                                                                            | 华为云：https://mirrors.huaweicloud.com/nodejs/ ；淘宝 npmmirror：https://npmmirror.com/mirrors/node/ （均免登录） |
| 4    | PostgreSQL                       | 14+（推荐 16）                                | **EDB 直链（免登录直下）：** https://sbp.enterprisedb.com/getfile.jsp?fileid=1260422 （PostgreSQL 16.15 Windows x64） | ⚠️**国内镜像无 Windows 安装包**（清华/华为云/阿里云都只有源码或 Linux 版），请用左侧 EDB 直链，详见 2.4    |
| 5    | Git（可选，不跑 Git 命令可跳过） | 任意                                          | https://git-scm.com/download/win （免登录直下）                                                                             | 华为云：https://mirrors.huaweicloud.com/git-for-windows/ （免登录直下）                                            |

## 2. 安装步骤

### 2.1 安装 Java（17 或 21）

> Java 版本要求：**17 及以上**即可（Spring Boot 3.3 支持 17~22）。装 17 或 21（LTS）都行，推荐 21，支持周期更长。

#### 官网下载页面的选项怎么选

打开 https://adoptium.net/temurin/releases/?version=21 后，页面上有几个下拉框，按下面选：

| 下拉框                     | 选什么                | 说明                                                                                                       |
| -------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------- |
| **Operating System** | `Windows`           | 你用的是 Windows                                                                                           |
| **Architecture**     | `x64`               | 绝大多数新电脑是 x64（Intel/AMD 芯片都选这个）；除非你的电脑是 ARM 芯片（比如部分新款 Surface）才选`ARM` |
| **Package Type**     | `JDK`               | ⚠️ 一定要选**JDK**，不要选 JRE（JRE 没有编译功能，跑不了 Maven/Spring Boot）                       |
| **Version**          | `21`（LTS）         | 也可选`17`，都行                                                                                         |
| **Latest Release**   | 默认最高的即可        | 例如`21.0.x`，直接用它给的默认值                                                                         |
| **Installer**        | `Windows x64  .msi` | ⚠️ 认准`.msi` 结尾的那个文件点下载。`.zip` 也能用但要手动配 PATH，不推荐                             |

> 选完点 **Download** 即开始下载，无需登录。

#### 镜像站备选（免登录，路径对应关系）

如果官网慢或打不开，用清华镜像。镜像目录结构是 `https://mirrors.tuna.tsinghua.edu.cn/Adoptium/{版本}/{jdk|jre}/{架构}/{系统}/`，所以：

- JDK 21 x64 Windows 直接进：https://mirrors.tuna.tsinghua.edu.cn/Adoptium/21/jdk/x64/windows/
- 下载文件名形如 `OpenJDK21U-jdk_x64_windows_hotspot_21.0.x.msi`（认准 `.msi`，别下成 JRE 或 zip）

#### 安装与验证

1. 双击下载好的 `.msi` 安装包。
2. 安装向导**一路 Next / 保持默认勾选**（默认会自动配置 `JAVA_HOME` 并把 `java` 加进 PATH，还会自动安装 JavaSoft ORACLE 之类，不用管）。
3. 验证：新开一个 PowerShell 窗口执行：

```powershell
java -version
```

看到类似 `openjdk version "21.0.x"`（或 `17.0.x`）即成功。

### 2.2 安装 Maven

任选一种方式：

**方式 A：解压并手动配置（推荐，与启动脚本兼容）**

1. 下载 `apache-maven-3.9.16-bin.zip`（**认准 `bin.zip`，别下 `src` 源码包**；`.tar.gz` 是 Linux/Mac 用的，Windows 不用）。
   - 官网直下：https://maven.apache.org/download.cgi
   - 免登录镜像：https://mirrors.tuna.tsinghua.edu.cn/apache/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip
2. 解压到 `D:\apache-maven-3.9.16`（解压后目录名即 `D:\apache-maven-3.9.16`）。

> 注意：项目的 `start-all.ps1` 和 `backend/start-backend.ps1` 会**自动在 `D:\` 下查找 `apache-maven*` 目录**，所以解压到 D 盘根目录即可被脚本自动识别，无需手动配 PATH。

3. 验证（新开 PowerShell）：

```powershell
D:\apache-maven-3.9.16\bin\mvn -version
```

**方式 B：配置全局 PATH（更通用）**

1. 解压到任意目录，如 `C:\Program Files\apache-maven-3.9.14`。
2. 按 `Win + R` → 输入 `sysdm.cpl` → 打开「环境变量」。
3. 新建系统变量 `MAVEN_HOME` = Maven 解压目录。
4. 编辑 `Path` 变量，新增 `%MAVEN_HOME%\bin`。
5. 验证（新开 PowerShell）：`mvn -version`。

### 2.3 安装 Node.js 20

1. 下载 Node.js 20 LTS（Windows x64 `.msi`）。
   - 官网直下：https://nodejs.org/zh-cn/download
   - 免登录镜像：https://mirrors.huaweicloud.com/nodejs/ 或 https://npmmirror.com/mirrors/node/
2. 双击安装，**保持默认选项**（自动安装 npm 并加入 PATH）。
3. 验证（新开 PowerShell）：

```powershell
node -v
npm -v
```

### 2.4 安装 PostgreSQL 并创建数据库

> **下载说明（重要）**：PostgreSQL 的 Windows 安装包**只有 EDB 官方提供**，清华/华为云/阿里云等国内镜像都只有源码或 Linux 版，没有 Windows 安装包。EDB 官网（`enterprisedb.com`）在境外，网页可能打不开——但**它的文件直链在国内是可以直接下载的，且免登录**。

1. 下载 PostgreSQL 16 Windows 安装包（`.exe`），三选一：

   - **方式一：EDB 官方直链（推荐，已验证可直接下载、免登录）**
     - PostgreSQL **16.15** Windows x64：`https://sbp.enterprisedb.com/getfile.jsp?fileid=1260422`
     - 浏览器打开该链接会直接开始下载，无需登录、无需填邮箱。
   - **方式二：EDB 官网下载页**（如果网页能打开）
     - https://www.enterprisedb.com/downloads/postgres-postgresql-downloads
     - 表格里找到 **16.15** 这一行，点 **Windows x86-64** 列的下载图标。
   - **方式三：官方下载站**（跳转到 EDB）
     - https://www.postgresql.org/download/windows/ → 点 "Download the installer"。

   > 注：以上直链指向的是当前最新 16.x（16.15）。若日后版本更新，直链 fileid 可能变化，届时用方式二从官网表格取最新 16.x 链接即可。
   >
2. 安装过程中 **PostgreSQL 超级用户 `postgres` 的密码请设置为：`2022S3414ycx`**

   > 原因：项目后端 `backend/src/main/resources/application.yml` 默认数据库配置为：
   >
   > ```
   > username: postgres
   > password: 2022S3414ycx
   > ```
   >
   > 设置相同密码即可零配置直接运行。若不想用这个密码，见第 4 节「修改数据库密码」。
   >
3. 端口保持默认 `5432`。
4. 安装完成后，通过开始菜单打开 **pgAdmin 4** 或命令行创建数据库：

   > 路径说明：本机 PostgreSQL 安装位置可能是 `C:\Program Files\PostgreSQL\18` 或 `D:\PostgreSQL\18`（取决于你选择装到哪个盘），下面以 D 盘为例。

```powershell
# 方式一：命令行（需把 psql 加入 PATH，或到安装目录执行）
D:\PostgreSQL\18\bin\psql -U postgres -c "CREATE DATABASE ai_self_learning;"

# 方式二：pgAdmin 4 图形界面
# 连接本地服务器 → 右键 Databases → Create → Database
# 名称填: ai_self_learning
```

5. 验证数据库可连接：

```powershell
D:\PostgreSQL\18\bin\psql -U postgres -d ai_self_learning -c "SELECT 1;"
```

（会提示输入密码 `2022S3414ycx`）

## 2.5 配置环境变量 PATH（关键，否则终端找不到命令）

> 工具装好后，如果终端里敲 `java` / `mvn` / `node` / `psql` 提示"无法识别/找不到命令"，就是因为它们的 `bin` 目录**没有加进系统 PATH**。配置一次后所有新终端即可直接使用。

### 你本机的实际安装路径（以此为准）

| 工具 | 需要加进 PATH 的目录 |
|------|---------------------|
| Java | `D:\Eclipse Adoptium\jdk-21.0.12.8-hotspot\bin` |
| Maven | `D:\apache-maven-3.9.16\bin` |
| Node.js | `D:\nodejs` |
| PostgreSQL | `D:\PostgreSQL\18\bin` |

> 如果你的安装路径不同，找到对应目录里的 `java.exe` / `mvn.cmd` / `node.exe` / `psql.exe` 所在文件夹即可。

### 方式一：图形界面配置（推荐新手）

1. 按 `Win + R`，输入 `sysdm.cpl` 回车，打开「系统属性」。
2. 右下角点**「环境变量(N)…」**。
3. 在**「系统变量」**区域选中 **`Path`**，点**「编辑」**。
4. 点**「新建」**，逐个添加以下四个路径（每行一个）：

   ```
   D:\Eclipse Adoptium\jdk-21.0.12.8-hotspot\bin
   D:\apache-maven-3.9.16\bin
   D:\nodejs
   D:\PostgreSQL\18\bin
   ```

5. 一路「确定」关闭所有窗口。
6. **重新打开**所有终端（VS Code 也要重开），然后验证：

```powershell
java -version
mvn -version
node -v
npm -v
psql --version
```

都显示版本号即配置成功。

### 方式二：命令行一键配置（需管理员 PowerShell）

右键「以管理员身份运行」PowerShell，执行：

```powershell
[Environment]::SetEnvironmentVariable("Path", $env:Path + ";D:\Eclipse Adoptium\jdk-21.0.12.8-hotspot\bin;D:\apache-maven-3.9.16\bin;D:\nodejs;D:\PostgreSQL\18\bin", "Machine")
```

执行后**重开所有终端**生效，再用上面的验证命令确认。

### 方式三：临时配置（不想改系统设置，仅当前窗口有效）

每次打开终端先执行一次：

```powershell
$env:Path = "D:\Eclipse Adoptium\jdk-21.0.12.8-hotspot\bin;D:\apache-maven-3.9.16\bin;D:\nodejs;D:\PostgreSQL\18\bin;" + $env:Path
```

> 提示：项目自带脚本 `start-all.ps1` / `backend/start-backend.ps1` 会自动在 `D:\` 下查找 `apache-maven*`，所以**只跑 `start-all.bat` 的话 Maven 不配 PATH 也能用**；但前端 `npm` 和 `java` 仍需要 PATH 或在命令前手动指定。

## 3. 检查项目配置是否已就绪

以下文件已随项目拷贝过来，**无需改动**：

- `backend/.env` — 包含阿里云 DashScope AI 配置（`OPENAI_API_KEY`、`OPENAI_BASE_URL`、`OPENAI_MODEL`），启动脚本会自动读取。
- `frontend/vite.config.js` — 前端开发代理，把 `/api` 和 `/uploads` 转发到 `http://localhost:5000`。

确认一下文件存在：

```powershell
Test-Path d:\zstp\backend\.env
```

应返回 `True`。若为 `False`，将 `backend/.env.example`（如有）复制为 `.env`，或手动创建并填入 AI 配置。

## 4.（可选）修改数据库密码

如果你安装 PostgreSQL 时使用了其他密码，有两种改法：

**方式 A：通过 `.env` 覆盖（推荐，不动源码）**

在 `backend/.env` 中追加：

```
POSTGRES_USER=postgres
POSTGRES_PASSWORD=你的密码
```

**方式 B：修改 `application.yml`**

编辑 `backend/src/main/resources/application.yml` 中的：

```yaml
password: ${POSTGRES_PASSWORD:你的密码}
```

## 5. 启动项目

### 一键启动（推荐）

在项目根目录双击或运行：

```powershell
cd d:\zstp
.\start-all.bat
```

脚本会自动：

1. 查找/使用 Maven 编译后端并启动在 `http://localhost:5000`。
2. 在前端目录执行 `npm install` 并启动开发服务在 `http://localhost:5173`。

浏览器打开 **http://localhost:5173**

### 手动启动（分步排查用）

> 前提：已按 2.5 配置好 PATH（或使用下方完整路径）。

**后端：**

```powershell
cd d:\zstp\backend
mvn spring-boot:run
```

> 若提示找不到 `mvn`，用完整路径：
> ```powershell
> D:\apache-maven-3.9.16\bin\mvn.cmd spring-boot:run
> ```
> 若提示找不到 `java`，先执行：
> ```powershell
> $env:Path = "D:\Eclipse Adoptium\jdk-21.0.12.8-hotspot\bin;" + $env:Path
> ```

**前端（另开一个终端）：**

```powershell
cd d:\zstp\frontend
npm install
npm run dev
```

> 若提示找不到 `npm`，先执行：
> ```powershell
> $env:Path = "D:\nodejs;" + $env:Path
> ```

## 6. 默认演示账号

由后端启动时自动初始化：

| 角色   | 用户名           | 密码            |
| ------ | ---------------- | --------------- |
| 学生   | `student_demo` | `student123`  |
| 教师   | `teacher_demo` | `teacher123`  |
| 管理员 | `admin_demo`   | `admin123456` |

## 7. 常见问题排查

| 现象                                                 | 原因与处理                                                                                                       |
| ---------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| `java -version` 找不到命令                         | 未配 PATH 或未装 JDK：按 2.5 配置 `D:\Eclipse Adoptium\jdk-21.0.12.8-hotspot\bin`，重开终端 |
| 启动脚本提示 Maven 未找到                            | 确认 Maven 解压到`D:\apache-maven-3.9.16`（脚本会自动在 D 盘查找 `apache-maven*`），或按 2.5 配 PATH         |
| 前端提示 `npm` / `node` 找不到命令                  | 按 2.5 把 `D:\nodejs` 加入 PATH，重开终端 |
| 后端报数据库连接失败（`Connection refused`）       | PostgreSQL 服务未启动：开始菜单 →`pgAdmin 4` 或运行 `net start postgresql-x64-18`；或密码不匹配，回到 2.4/4 |
| 后端报`database "ai_self_learning" does not exist` | 未创建数据库，回到 2.4 第 4 步                                                                                   |
| 前端`npm install` 慢/失败                          | 换国内镜像：`npm config set registry https://registry.npmmirror.com` 后重试                                    |
| 后端正常但前端 5000 无法访问                         | 等待后端完全启动（首次启动会下载 Maven 依赖，较慢）；观察后端终端日志                                            |
