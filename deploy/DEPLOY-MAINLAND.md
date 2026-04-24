# 大陆服务器部署：零基础一步步教程

### 要收藏的网址


| 干什么                    | 在浏览器地址栏输入                                      |
| ---------------------- | ---------------------------------------------- |
| 腾讯云首页（注册、登录）           | `https://cloud.tencent.com`                    |
| 买轻量服务器                 | `https://cloud.tencent.com/product/lighthouse` |
| 云控制台（找服务器、搜「轻量」）       | `https://console.cloud.tencent.com`            |
| 域名（DNSPod）             | `https://dnspod.cloud.tencent.com`             |
| 域名解析列表                 | `https://console.dnspod.cn/dns/list`           |
| 备案介绍/入口                | `https://cloud.tencent.com/product/ba`         |
| 下载 WinSCP（传文件用，可选）     | `https://winscp.net/eng/download.php`          |
| 下载 Git for Windows（可选） | `https://git-scm.com/download/win`             |


---

## 读完这一段再动手

你要做的事可以概括成下面 **5 大块**，按顺序做，**不要跳步**。

1. **注册云账号 + 实名认证**（和注册淘宝类似，必须真人实名）。
2. **买一台大陆的服务器**（选 Ubuntu 系统，像租一台远在机房的电脑）。
3. **买一个域名**（例如 `xxx.com`，评委用网址访问你的网站）。
4. **做 ICP 备案**（国家要求，**一般要 7～20 个工作日**，所以要**尽早开始**）。
5. **在服务器里装软件、把你的项目跑起来、配上 HTTPS**（后面会手把手写复制哪段命令）。

---

## 名词大白话


| 词                  | 什么意思                                      |
| ------------------ | ----------------------------------------- |
| **云服务器 / 轻量应用服务器** | 机房里的电脑，24 小时开机，有公网 IP，你远程操作它。             |
| **公网 IP**          | 一串数字，例如 `123.45.67.89`，像这台服务器在互联网上的「门牌号」。 |
| **域名**             | 给人记的网址名字，例如 `www.abc.com`。                |
| **备案（ICP 备案）**     | 在大陆用域名开网站前，向管局登记。在云厂商网页里填表即可。             |
| **解析**             | 告诉互联网：`你的域名` → 指向 `你的服务器公网 IP`。           |
| **SSH / 登录服务器**    | 用命令行远程控制那台 Ubuntu 电脑。                     |
| **HTTPS**          | 地址栏有小锁，`https://` 开头，浏览器认为连接加密。           |


---

## 本机需要准备什么（Windows）

下面这些 **不是在服务器上装**，是在 **你自己的电脑上** 装，方便你传文件、敲命令。

### 1. 浏览器

用 **Chrome**、**Edge** 都可以。下面统一说「打开浏览器」。

### 2. 登录服务器的方式（三选一即可）


| 方式                              | 要不要额外下软件          | 适合谁            |
| ------------------------------- | ----------------- | -------------- |
| **A. 腾讯云网页里点「登录」**              | 不要                | 最怕命令行的人        |
| **B. Windows 自带 SSH**           | 不要（Win10/11 一般自带） | 愿意打开「终端」的人     |
| **C. WinSCP 传文件 + 网页或 SSH 敲命令** | 要装 WinSCP         | 不用 Git、想拖文件夹上传 |


**下载 WinSCP（若选 C）**

1. 浏览器打开：`https://winscp.net/eng/download.php`
2. 点 **Download WinSCP**，下载 `.exe` 安装包。
3. 双击安装，一路「下一步」即可。

**下载 Git（若你打算用 Git 把代码同步到服务器）**

1. 浏览器打开：`https://git-scm.com/download/win`
2. 下载安装包，双击安装，**全部保持默认选项**即可。
3. 装好以后会有「Git Bash」程序；也可以继续用后面说的 PowerShell。

---

# 第一部分：腾讯云账号与实名

## 第 1 步：打开腾讯云并注册

1. 打开浏览器，在地址栏输入并访问：
  `**https://cloud.tencent.com`**
2. 点页面上的 **注册 / 登录**，用手机号或微信按提示注册一个账号。
3. 注册后一定要完成 **实名认证**（个人就选个人认证，按页面传身份证）。
  **不实名的话，买不了服务器和域名，也备不了案。**

---

# 第二部分：买一台「轻量应用服务器」

## 第 2 步：进入购买页

1. 仍登录腾讯云状态下，地址栏输入：
  `**https://cloud.tencent.com/product/lighthouse`**  
   （这是「轻量应用服务器」产品介绍页。）
2. 点 **立即购买** 或 **选购**（按钮名字可能略有变化）。

## 第 3 步：购买时怎么选

在下单页面里 **依次确认** 这些选项（找不到就用页面上的 **搜索框** 搜关键字）：

1. **地域**：选 **中国大陆** 任意城市（例如 **广州**、**上海**），不要选香港、新加坡（本教程按 **大陆备案** 写的）。
2. **套餐**：选 **2 核 4G** 或以上（内存大点更稳，编译 Java 不容易卡死）。
3. **镜像**：选 **Ubuntu**，版本选 **22.04 LTS**（没有就选尽量新的 Ubuntu 长期支持版）。
4. **流量包 / 硬盘**：用默认一般够用；系统盘至少 **40GB** 更省心。
5. **时长**：轻量云常见 **最短就是 1 年**，直接选 **1 年** 即可，比赛结束后机器还可以继续当练习环境用；若页面有 **新用户秒杀、学生优惠** 等更短套餐，以 **当下购买页显示为准**。
6. 设置 **root 密码**（或密钥）：**一定要用笔记本记下来**，后面登录全靠它。密码要够长、够复杂。

### 购买页上这几个选项怎么选

腾讯云轻量下单页常见有 **应用创建方式、应用模板、域名解析、服务器名称、登录方式**。界面若改版，字眼可能略有不同，按下面意思选即可。


| 页面上的名字     | 建议你怎么选                                                                                                                                   | 原因                                                                                                       |
| ---------- | ---------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| **应用创建方式** | 选 **从系统镜像创建**（或 **基于系统镜像** / **仅系统**，不要选成「从应用模板一键建站」那类）                                                                                  | 本教程是自己在 Ubuntu 里装 Docker、再跑你的项目；预装 WordPress、宝塔、论坛等模板 **用不上**，反而添乱。                                      |
| **应用模板**   | **只有**你还在「应用镜像/模板」那条路上时才出现。若必须点模板：找 **Ubuntu 22.04**、**纯净版**（没有就选最干净的系统类）。已选「系统镜像」时，模板常为「无」或不必管。                                         | 模板多是现成网站；你需要的是 **空 Ubuntu**。                                                                             |
| **域名解析**   | 域名 **还没买好 / 没备案好**：选 **暂不绑定**、**不自动解析** 或 **跳过**（有就取消勾选）。域名已在腾讯云且希望省事：**可以**勾选「自动解析」，但 **备案通过后仍建议** 到 DNSPod 核对 A 记录是否指向这台机子的 **公网 IP**。 | 你之前说域名未就绪时，不必在这里强绑；后面教程「第 9 步」会手动解析。                                                                     |
| **服务器名称**  | 随便填，例如 `zstp-bisai` ，仅控制台里好认                                                                                                             | **不影响**网站访问，只是备注名。                                                                                       |
| **登录方式**   | **零基础选「密码」**：给 **root**（或页面提示的管理员账号）设一个强密码并记下                                                                                            | 教程按 **密码登录** 写的（网页登录、PowerShell `ssh`、WinSCP 都要这个密码）。选 **SSH 密钥** 也可以，但要在自己电脑上生成密钥对、上传公钥，**步骤多**，不熟悉先别选。 |


**一句话**：创建方式走 **系统镜像 → Ubuntu 22.04**；域名先 **别自动绑** 也行；名称随便；登录用 **密码**。

付款完成后，进入 **控制台**。

## 第 4 步：在控制台找到你的服务器

1. 打开：`https://console.cloud.tencent.com/`
2. 页面顶部有 **搜索框**，输入 `**轻量应用服务器`**，点进对应产品。
3. 你会看到 **一台实例**，记下两样东西（后面到处要用）：
  - **公网 IP**（一串数字）  
  - **重置密码 / root 密码**（若忘了可在控制台「重置」）

## 第 5 步：放行防火墙端口

评委要访问网站，服务器要开 **80** 和 **443**；你要远程管理要开 **22**。

1. 在 **轻量应用服务器** 列表里，点你的 **实例名称** 进入详情。
2. 找 **防火墙** 或 **安全组** 相关 tab。
3. **添加规则**（若没有就新建）：

  | 协议  | 端口  | 来源                   |
  | --- | --- | -------------------- |
  | TCP | 22  | 全部 IPv4（或 0.0.0.0/0） |
  | TCP | 80  | 全部 IPv4              |
  | TCP | 443 | 全部 IPv4              |

4. **不要** 自己加 5432、8080、5000 对全网开放（数据库和后端只给本机用，开放了反而不安全）。

---

# 第三部分：买域名

## 第 6 步：注册域名

1. 登录腾讯云后，打开：`**https://dnspod.cloud.tencent.com/`**（DNSPod 域名，腾讯云常用入口）。
2. 在 **域名注册** 里搜一个还没被注册的名字。
3. 加入购物车 → 结账 → 按提示付 **年费**。
4. 买完后按提示做 **域名实名认证**。**不实名，后面解析、备案会卡住。**

**记下你的完整域名**，例如：`myproject.cn` 或 `www.yourteam.com`。下文叫 **「你的域名」**。

---

# 第四部分：ICP 备案

## 第 7 步：打开备案入口

1. 打开：`https://cloud.tencent.com/product/ba`
  或在控制台顶部搜索 `**备案`**，进入 **网站备案 / ICP 备案**。
2. 点 **开始备案** / **新增备案**，按向导走。

## 第 8 步：备案一般要填什么

不同主体（学生个人 / 学校）表格略有差别，常见会要：

- **主体信息**：姓名、身份证号、住址、手机号、人脸识别。  
- **网站信息**：  
  - **网站名称**、**网站服务内容**（选「其他」或「计算机类」等，按真实写）。  
  - **域名**：填你刚买的 **你的域名**。
- **云资源**：选择你买的 **轻量应用服务器**（系统会把备案和这台机器关联合规）。

过程中若要求 **备案服务码**，在轻量服务器详情里一般能生成或查看，按腾讯云提示复制即可。

**接下去**：等腾讯云初审 → 可能要你 **拍幕布照片或视频**（按短信/网页提示）→ **管局审核**。  
**只有收到「备案成功」通知后**，用域名对外开网站才完全符合大陆规则。

> **备案审核期间**：你可以先在服务器上把网站装好，用 **公网 IP 的临时方式** 自测（常为 `http://IP`，且无正式 HTTPS），**但不要把这个当最终交给评委的链接**。

---

# 第五部分：备案成功后——解析域名

## 第 9 步：把域名指到你的服务器 IP

1. 打开 DNSPod：`https://console.dnspod.cn/dns/list`
2. 点你的 **域名**，进入 **解析**。
3. 点 **添加记录**，填：
  - **主机记录**：填 `@`（表示主域名，如 `myproject.cn`）；若你希望是 `www.xxx.cn`，再单独加一条主机记录 `www`。  
  - **记录类型**：**A**  
  - **记录值**：填你抄下来的 **公网 IP**  
  - TTL：默认即可
4. 保存。等几分钟到几小时，全球 DNS 会生效（本机可用 `ping 你的域名` 看是否已是该 IP，不会 ping 也没关系，后面用浏览器试）。

---

# 第六部分：登录服务器

下面任选 **一种** 方式。

## 方式 A：完全不用装软件——用腾讯云网页终端

1. 进入 **轻量应用服务器** → 点你的实例。
2. 找 **登录** / **一键登录** / **远程连接**。
3. 用 **密码** 登录（用户名一般是 **root**，密码是你买机器时设的或重置后的）。
4. 出现一个黑色窗口，可以打字，这就是 **服务器的命令行**。

## 方式 B：Windows 自带的「终端」里 SSH

1. 在你电脑上按 `Win` 键，输入 `**PowerShell`**，打开 **Windows PowerShell**。
2. 输入（把 `你的公网IP` 换成真实数字）：
  ```text
   ssh ubuntu@你的公网IP
  ```
3. 第一次会问 `yes/no`，输入 `**yes**` 回车。
4. 提示 `password` 时，**输入 root 密码**（输入时屏幕不显示，正常），回车。
5. 登录成功后，提示符会变成类似 `ubuntu@VM-0-ubuntu:~#`，说明你已经进到服务器里了。

---

# 第七部分：在服务器里安装 Docker、Nginx、证书工具

下面的命令，都是 **登录服务器之后**，在黑色窗口里 **一行一行粘贴**（可以整段复制，但建议先更新再装 Docker）。

## 第 10 步：更新系统软件列表

复制下面整段，在服务器窗口里 **右键粘贴**（网页终端一般是粘贴按钮），回车执行：

```bash
sudo apt update && sudo apt upgrade -y
```

- `**sudo**`：用管理员权限执行。  
- 若问 `[Y/n]`，输入 `**y**` 回车。

## 第 11 步：安装 Docker

依次复制执行（可以一起复制整段）：

```bash
sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
```

装完后执行：

```bash
sudo usermod -aG docker "$USER"
```

然后 **一定要关掉 SSH、重新登录一次**（网页终端就断开重连）。  
重登后验证 Docker：

```bash
docker --version
docker compose version
```

能看到版本号就成功。

若 `**curl` 下载特别慢**：那是访问国外源慢。可以换用手机热点重试，或搜索「Ubuntu Docker 阿里云镜像源」按文章换国内源。

## 第 12 步：安装 Nginx 和 Certbot

```bash
sudo apt install -y nginx certbot python3-certbot-nginx
sudo systemctl enable --now nginx
```

---

# 第八部分：把项目放到服务器上

## 第 13 步：建文件夹

```bash
sudo mkdir -p /opt/zstp
sudo chown "$USER:$USER" /opt/zstp
cd /opt/zstp
```

## 第 14 步：任选一种方式把代码弄上来

### 方法 1：代码在 GitHub / Gitee（推荐）

1. 若敲 `git` 提示找不到命令，先在服务器执行：`**sudo apt install -y git**`
2. 在浏览器打开你的仓库页面，点 **克隆地址**，复制 **HTTPS** 链接。
3. 在服务器执行（把地址换成你的）：

```bash
cd /opt/zstp
git clone https://github.com/你的用户名/你的仓库名.git .
```

若目录里已有东西导致失败，先 `cd ~`，再 `git clone https://你的仓库地址.git zstp-temp`，然后：

```bash
sudo rm -rf /opt/zstp/*
sudo mv zstp-temp/* /opt/zstp/
sudo rmdir zstp-temp 2>/dev/null || sudo rm -rf zstp-temp
```

### 方法 2：不用 Git，用 WinSCP 拖文件夹

1. 在你 **自己电脑** 上打开 WinSCP，新建会话：
  - 文件协议：**SFTP**  
  - 主机名：**你的公网 IP**  
  - 用户名：**root**  
  - 密码：**root 密码**
2. 登录后，左边是你电脑，右边是服务器。
3. 把你电脑上 **整个项目文件夹**（里面有 `deploy`、`backend`、`frontend`）拖到服务器 `**/opt/zstp`** 里。
  **注意**：拖过去后，服务器上应是 `/opt/zstp/deploy`、`/opt/zstp/backend`……

---

# 第九部分：配置密码并启动网站

## 第 15 步：创建 .env 文件

```bash
cd /opt/zstp/deploy
cp .env.example .env
nano .env
```

会出现编辑器：建议按下面 **A → B → C** 依次填写，最后保存退出。

**A. 数据库密码**

1. 你会看到一行 `POSTGRES_PASSWORD=...`
2. 把 `=` 后面改成 **一长串英文+数字密码**（自己乱打一段 20 位以上，**记下来**）。

**B. AI 大模型**

`deploy/.env.example` 里已有示例（复制成 `.env` 后默认是注释掉的）。若要让 **AI 助手、依赖大模型的功能** 真正连上模型，请在 **同一份 `.env`** 里增加或取消注释并填写：


| 变量名               | 是否必填           | 说明                                                                                               |
| ----------------- | -------------- | ------------------------------------------------------------------------------------------------ |
| `OPENAI_API_KEY`  | 想用 AI 则 **必填** | 你的 API 密钥（常见形如 `sk-...`）。**不要**把密钥发到群聊、不要提交到 Git；只写在服务器这份 `.env` 里。                              |
| `OPENAI_BASE_URL` | 可选             | 不配时默认 `https://api.openai.com/v1`。若使用 **兼容 OpenAI 协议** 的国内网关或自建接口，改成对方提供的地址（是否带 `/v1` 以服务商文档为准）。 |
| `OPENAI_MODEL`    | 可选             | 不配时默认 `gpt-4o-mini`。若网关要求固定模型名，按服务商说明填写。                                                         |


不配 `OPENAI_API_KEY` 时，网站其它功能一般仍正常，但用户会看到类似 **「AI 服务暂未启用，请联系管理员配置 OPENAI_API_KEY」** 的提示，这是预期行为。

**C. 保存退出**

1. 按 `**Ctrl + O`** 回车保存，再按 `**Ctrl + X`** 退出。

**若你以后再改 `.env`（例如补上了 `OPENAI_API_KEY`）**：改完保存后，必须在第 16 步里 **重新执行** `docker compose up -d --build`，或至少执行 `docker compose up -d --force-recreate backend`，否则 **后端容器仍用旧环境变量**，页面上还是会显示未启用。

## 第 16 步：启动

仍在 `/opt/zstp/deploy` 下执行：

```bash
docker compose up -d --build
```

第一次会很久（下载镜像、编译 Java），**有耐心**。  

若你看到前端构建长时间卡在 `web` 服务的 `npm ci`：

```bash
docker compose build web --no-cache --progress=plain
```

观察是否反复卡在依赖下载（常见于国外源网络慢）。本仓库当前 `frontend/Dockerfile` 已默认使用国内 npm 镜像源；如果你曾在服务器上缓存过旧镜像层，请加 `--no-cache` 强制重建一次。

看是否在跑：

```bash
docker compose ps
```

三个服务都 `running` 较好。看后端日志：

```bash
docker compose logs -f backend
```

按 `**Ctrl + C**` 退出日志（不会关网站）。

**关于 AI 是否生效**：日志里若出现 `**AI enabled`**（并带有脱敏后的 key 信息），说明后端已读到 `OPENAI_API_KEY`。若出现 `**AI disabled because OPENAI_API_KEY is empty`**，说明密钥仍为空或未传入容器，请回到 **第 15 步 B** 检查 `.env`，并确认已按第 15 步末尾 **重建 backend 容器**。

## 第 17 步：在服务器上自测

```bash
curl -sS http://127.0.0.1:8080/api/health
```

若返回里有 `"status":"ok"` 一类 JSON，说明 **前端 Nginx + 后端 + 数据库** 基本通了。

---

# 第十部分：申请 HTTPS

## 第 18 步：准备网页验证目录

```bash
sudo mkdir -p /var/www/certbot
```

## 第 19 步：用 Certbot 申请证书

把下面命令里的 `**你的域名**` 换成真实域名（不要带 `https://`，例如只写 `www.abc.com` 或 `abc.com`）：

```bash
sudo certbot --nginx -d 你的域名
```

按提示：

1. 输入 **邮箱**（用于证书到期提醒）。
2. 同意服务条款：输入 `**Y`**。
3. 是否分享邮箱：可输入 `**N`**。
4. Certbot 可能问是否把 HTTP 自动跳 HTTPS，选 `**2`** 或推荐项。

成功后会显示 **Congratulations** 之类字样。

## 第 20 步：让 Nginx 把访问转到你 docker 里的网站

Certbot 会改 Nginx 配置，但 **默认可能还不会指向我们 docker 的 8080**。你要检查一次：

```bash
sudo nano /etc/nginx/sites-available/default
```

如果 Certbot 用的是 `sites-enabled` 里另一个文件，可以用：

```bash
ls /etc/nginx/sites-enabled/
sudo nano /etc/nginx/sites-enabled/你的域名对应的那份配置
```

在 `**server { listen 443 ssl ... }**` 那个块里，找到 `**location /**`，改成 **里面只有**（或合并进现有）下面这一段（注意不要少了分号）：

```nginx
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        client_max_body_size 50m;
        proxy_connect_timeout 300s;
        proxy_send_timeout 300s;
        proxy_read_timeout 300s;
    }
```

保存退出：`**Ctrl + O**` 回车，`**Ctrl + X**`。

然后检查并重载：

```bash
sudo nginx -t && sudo systemctl reload nginx
```

## 第 21 步：用浏览器验收

在你 **自己电脑** 浏览器地址栏输入：

```text
https://你的域名
```

应出现 **登录页面**。若提示 **不安全** 或 **证书错误**，多半是域名写错、或解析没到本机、或 80/443 没放行。

---

---

# 第十一部分：仍然打不开——按表自查


| 现象                                    | 你先检查什么                                                                                                                                                                                                                                            |
| ------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 浏览器连不上                                | 备案是否真的 **已通过**？域名 **A 记录** 是否指向 **现在这台** 公网 IP？                                                                                                                                                                                                   |
| 只有 IP 能开会话                            | **域名 + 备案** 未完成时很常见，继续等备案或先用 IP 自测（给评委还是要域名 HTTPS）。                                                                                                                                                                                               |
| **502**                               | `docker compose ps` 是否都在跑；`docker compose logs web backend` 有无报错。                                                                                                                                                                                 |
| **证书申请失败**                            | 域名是否已解析到本机；**80 端口** 是否放行；服务器上 `sudo systemctl status nginx` 是否正常。                                                                                                                                                                                |
| **忘记数据库密码**                           | 看服务器上 `/opt/zstp/deploy/.env` 里的 `POSTGRES_PASSWORD`（不要发给别人）。                                                                                                                                                                                     |
| **页面提示「AI 服务暂未启用…配置 OPENAI_API_KEY」** | 在 `/opt/zstp/deploy/.env` 中填写 `OPENAI_API_KEY`（及网关需要的 `OPENAI_BASE_URL` / `OPENAI_MODEL`），保存后在 `deploy` 目录执行 `docker compose up -d --build` 或 `docker compose up -d --force-recreate backend`，再看 `docker compose logs backend` 是否出现 `AI enabled`。 |


---

## 附录：本仓库里和部署相关的文件

- `deploy/docker-compose.yml`：一键启动数据库 + 后端 + 网站（其中 `OPENAI`_* 从 `.env` 传入后端容器）  
- `deploy/.env.example`：`.env` 模板（含数据库密码与可选的 AI 变量说明）  
- `deploy/host-nginx.example.conf`：仅供参考的合成配置（你按上面「第 20 步」改系统里的 Nginx 即可）

---

**最后提醒**：大陆 **正规对外访问** = **大陆机器 + 已备案域名 + HTTPS**。你按本教程顺序做完，评委用 `**https://你的域名`** 即可访问你的比赛作品。