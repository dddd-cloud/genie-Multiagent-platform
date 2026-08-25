# Genie Multi-Agent Platform

> 基于 [JoyAgent-JDGenie](https://github.com/jd-opensource/joyagent-jdgenie) 二次开发的多用户、可配置、可扩展多智能体平台。

![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)
![Java](https://img.shields.io/badge/Java-17-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.2-brightgreen.svg)
![Node.js](https://img.shields.io/badge/Node.js-20-brightgreen.svg)
![React](https://img.shields.io/badge/React-19-61DAFB.svg)
![Python](https://img.shields.io/badge/Python-3.11-3776AB.svg)
![Docker Compose](https://img.shields.io/badge/Docker%20Compose-supported-2496ED.svg)

## 简介

Genie Multi-Agent Platform 是在京东开源项目 **JoyAgent-JDGenie** 基础上的二次开发版本。

保留了原项目的 ReAct、Plan & Executor、多智能体协作、Deep Search、报告生成、代码执行、文件处理、数据分析和 SSE 流式输出等核心能力，并进一步将其扩展为一个面向真实多用户场景的智能体平台：用户可以管理自己的模型、专家 Agent、专家团队、Skill 和 MCP 连接，在 `Auto`、`Solo`、`Ensemble` 三种模式下完成单智能体或多智能体任务，也可以在隔离的浏览器工作区中管理输入文件与智能体生成的交付物。


- 仓库地址：<https://github.com/dddd-cloud/genie-Multiagent-platform.git>
- 上游项目：<https://github.com/jd-opensource/joyagent-jdgenie>
- 开源许可：Apache License 2.0，详见 [LICENSE](./LICENSE)

## 核心能力

| 能力 | 说明 |
| --- | --- |
| 多种执行模式 | `Auto` 自动选择一个专家或团队；`Solo` 指定单个在线专家；`Ensemble` 使用指定团队或自定义专家集合协作。 |
| 可配置 Agent 与 Team | 支持 Agent 创建、编辑、上下线、模型与 Prompt 配置、Skill/MCP 绑定，以及带主控人格的团队配置。 |
| 多智能体编排 | 支持规划、串并行步骤、子任务拆分、执行重试、降级回退、结果汇总和实时思考过程时间线。 |
| 资源广场 | 提供专家、专家团队、SkillHub Skill 与 MCP 资源入口；安装后生成当前用户真正拥有的资源及绑定关系。 |
| Skill 运行时 | 支持文件系统 Skill Package、`SKILL.md` 导入、Prompt 编译，以及受控的浏览器 Pyodide Python Skill 执行。 |
| MCP 管理 | 支持 MCP Server 配置、凭据加密、连通性测试、工具发现、启停和 Agent/Skill 工具绑定。 |
| 浏览器工作区 | 支持多工作区、文件夹、上传、预览、重命名、移动、删除、Python 数据处理和生成文件回收。 |
| 浏览器执行沙箱 | Skill 包校验后在独立 Pyodide Worker 中运行，仅加载当前工作区明确授权的文件，并提供输入输出限额、超时终止和崩溃重建。 |
| 会话与记忆 | 支持会话持久化、历史上下文、附件、流式快照恢复、长期记忆与会话摘要。 |
| 多用户与管理 | 支持登录会话、CSRF 防护、用户隔离、管理员用户管理、会话撤销和模型用量统计。 |
| 原版工具能力 | 保留搜索、报告、代码解释器、文件工具、NL2SQL、表格 RAG 和自动数据分析等能力。 |

## 与京东原版 JoyAgent-JDGenie 的对比

### 对比口径

这里的“原版”指本项目开始二次开发时使用的 JoyAgent-JDGenie 上游代码与原 README 所描述的能力，不代表京东仓库后续分支的所有变化。二次开发没有替换原有智能体内核，而是在其上增加平台层、配置层、持久化层和新的编排运行时。

### 功能与技术差异

| 维度 | JoyAgent-JDGenie 原始能力 | 本项目扩展 | 主要技术实现 |
| --- | --- | --- | --- |
| 产品定位 | 已是开箱即用的端到端通用多智能体产品，包含前端、后端、智能体和工具服务；但主要面向单实例使用，缺少多用户平台化管理能力 | 面向多用户的可配置多智能体平台 | 在原 `agent`、`tool` 和 SSE 链路外增加 `platform` 领域层 |
| 身份与数据隔离 | 已具备完整产品入口和任务执行链路；但没有登录 Session、用户角色、租户/用户数据隔离和管理员后台 | 登录、用户角色、用户状态、租户/用户数据隔离、管理员后台 | Spring Security、Spring Session JDBC、MySQL、CSRF、Flyway |
| 会话管理 | 已支持任务执行、上下文传递和流式回答；但没有结构化会话 CRUD、消息状态持久化、聊天附件和流式快照恢复 | 会话 CRUD、标题生成、消息状态机、附件、历史恢复、失败恢复和流式快照 | `conversation` / `conversation_message` / `conversation_attachment` 表，SSE Observer 持久化 |
| Agent 管理 | 已内置报告、搜索、代码、文件等子 Agent，并支持挂载 Java Tool；但没有用户级 Agent CRUD、上下线、测试及 Skill/MCP 绑定管理 | 用户可创建、编辑、测试、上下线 Agent，并配置模型、Prompt、Skill 与 MCP | Agent Definition、Prompt Compiler、运行时 Catalog、乐观版本控制 |
| 团队协作 | 已支持多 Agent、任务拆分、上下文管理和高并发 DAG 执行；但没有可持久化配置的 Team、主控人格、成员管理及 Auto/Solo/Ensemble 选择 | 可管理 Team、主控人格和成员；支持 Auto 交接、Solo 专家和 Ensemble 团队执行 | Team Runtime Resolver、路由模型、串并行编排、重试与 fallback |
| 流式输出与编排可视化 | 已支持全链路流式输出；但流式过程没有按平台会话持久化，也缺少快照恢复、版本化编排事件和可重放的多专家时间线 | 保留原有 SSE，并增加消息持久化、流式快照恢复、并行专家轨迹、工具状态和可重放编排时间线 | SSE Observer、版本化 orchestration event/trace、Snapshot、前端 reducer 与 timeline |
| 模型配置 | 已支持 OpenAI-compatible 模型，并可通过后端配置选择模型参数；但主要是全局静态配置，没有用户模型 CRUD、密钥加密、请求级选择和用量统计 | 系统默认模型与用户自定义 OpenAI-compatible 模型并存，可按请求选择 | User Model Catalog、请求级 LLM Settings、API Key 加密、Token 计量 |
| Skill | 已有丰富的内置 Agent、工具和 Prompt 能力；但没有独立的 Skill 领域模型、Package 导入、用户绑定和浏览器 Skill 运行时 | Skill CRUD、ZIP Package 导入、Agent 绑定、兼容型 Skill 运行时与浏览器 Python Skill | `SKILL.md` 解析、Package Guard、Pyodide Web Worker、执行结果回传协议 |
| MCP | 已支持通过配置挂载一个或多个 MCP Server 并调用远端工具；但没有用户级 Server CRUD、凭据加密、工具发现管理和细粒度绑定 | 用户级 MCP 管理、加密凭据、工具发现、启停和最小权限绑定 | 独立 FastAPI MCP Client、MCP 表模型、内部 Token、工具能力快照 |
| 资源生态 | 已支持通过代码新增子 Agent 和 Tool，扩展机制清晰；但资源需要手工开发或配置，没有统一资源广场、模板目录和用户级安装流程 | 专家/团队模板、内置 Skill、SkillHub 和 MCP 资源聚合与真实安装 | Marketplace Catalog、用户级资源安装、内容哈希复用、隐藏资源构建器 |
| 文件与工作区 | 已具备文件 Agent、服务端文件处理和 HTML/PPT/Markdown 等交付能力；但没有浏览器本地多工作区、文件夹管理、会话作用域和生成文件导入机制 | 多个浏览器本地工作区、文件夹、数据分析、会话关联与生成文件导入 | IndexedDB、localStorage、Pyodide、受限文件索引、远端文件适配器 |
| 浏览器执行沙箱 | 已有服务端代码解释器和文件处理工具；但没有浏览器工作区文件挂载沙箱，也没有基于显式文件授权的 Worker 隔离执行 | Skill 在独立 Worker 中执行，只加载显式授权文件；限制包路径、文件数量和大小，超时或崩溃后销毁运行实例 | ZIP/Manifest/路径校验、Pyodide Web Worker、Workspace Execution Bridge、Worker 重建 |
| 记忆 | 已提出并实现跨任务 Workflow Memory 的核心思路；但没有当前项目中的用户级长期记忆文件、会话摘要、自动捕获和管理界面 | 用户级长期记忆、会话摘要、自动捕获、分析与可视化管理 | 磁盘 Markdown 存储、Memory Guard、摘要/补丁协议、用户路径隔离 |
| 工程保障 | 已提供 Dockerfile、手动部署、一键启动脚本和基础测试；但没有平台级契约冻结、数据库迁移验证和当前这套单元/集成/E2E/真实服务分层验收 | 契约冻结、数据库迁移、单元/集成/E2E/真实服务验收和可恢复的本地开发栈 | JSON Schema、Testcontainers、Vitest、Playwright、Docker Compose、验收脚本 |


### 仍然继承的原版核心

- ReAct 与 Plan & Executor 智能体模式
- 多智能体任务分解、上下文管理与 DAG 执行思想
- Deep Search、报告生成、代码解释器、文件处理和数据分析工具
- HTML、Markdown、图表等交付物生成能力
- Java 后端、Python 工具服务、MCP Client 与 React 前端的基本服务划分
- OpenAI-compatible 模型接入方式和全链路流式输出

## 案例展示视频

等待覆盖的视频：
工作区演示

https://github.com/user-attachments/assets/6d965045-0f24-4284-80bb-a4dd07484207

多专家协作

https://github.com/user-attachments/assets/38adb69a-6146-48b9-b11d-065c8e08f8a8

资源广场


https://github.com/user-attachments/assets/e2ab2357-64d4-4acd-92b3-db63d2a2ea71


自然语言创建资源



https://github.com/user-attachments/assets/b67de35c-82c1-48c6-97a4-301438744504


完整平台演示


https://github.com/user-attachments/assets/f927595b-3a1e-4bd5-90ae-0f9f5c703c55




<table>
  <thead>
    <tr>
      <th>案例</th>
      <th>说明</th>
      <th>视频</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>Auto 智能调度及浏览器工作区</td>
      <td>系统根据问题自动选择一个专家或专家团队，上传数据、调用 Python 分析并保存生成文件</td>


      <td><video src="https://github.com/user-attachments/assets/36e39700-34f7-491a-a7dc-1b3eeb73bd2f" width="240" controls><a href="https://github.com/user-attachments/assets/36e39700-34f7-491a-a7dc-1b3eeb73bd2f">查看视频</a></video></td>
    </tr>
    <tr>
      <td>Ensemble 多专家协作</td>
      <td>多个专家并行分析、重试并汇总最终答案</td>
      <td><video src="https://github.com/user-attachments/assets/7eb8ed25-fd7c-434f-9324-c2c4452e6e43" width="240" controls><a href="https://github.com/user-attachments/assets/7eb8ed25-fd7c-434f-9324-c2c4452e6e43">查看视频</a></video></td>
    </tr>
    <tr>
      <td>资源广场</td>
      <td>安装专家团队、Skill 或 MCP，并在对话中真实调用</td>
      <td><video src="https://github.com/user-attachments/assets/597403c8-7b67-467c-b14e-78cd71985167" width="240" controls><a href="https://github.com/user-attachments/assets/597403c8-7b67-467c-b14e-78cd71985167">查看视频</a></video></td>
    </tr>
    <tr>
      <td>自然语言创建资源</td>
      <td>通过系统资源构建器创建 Agent 或 Team</td>
      <td><video src="https://github.com/user-attachments/assets/a545f25a-9e39-4e21-b556-bf4cf5a9a490" width="240" controls><a href="https://github.com/user-attachments/assets/a545f25a-9e39-4e21-b556-bf4cf5a9a490">查看视频</a></video></td>
    </tr>
    <tr>
      <td>完整平台演示</td>
      <td>集中展示平台的主要二次开发能力与操作流程</td>
      <td><video src="https://github.com/user-attachments/assets/6ed08acd-9c81-4613-b926-e4a9ee9138e3" width="240" controls><a href="https://github.com/user-attachments/assets/6ed08acd-9c81-4613-b926-e4a9ee9138e3">查看视频</a></video></td>
    </tr>
  </tbody>
</table>

## 系统架构

<img width="1672" height="941" alt="二开架构图" src="https://github.com/user-attachments/assets/a300bcc7-466a-462d-9314-4f38cb32d580" />


### 服务边界

| 服务 | 技术栈 | 默认端口 | 职责 |
| --- | --- | --- | --- |
| `ui` | React 19、TypeScript、Vite 6、Ant Design、Tailwind CSS | `3000` | 产品界面、SSE 消费、工作区、编排可视化、Pyodide Skill 执行 |
| `genie-backend` | Java 17、Spring Boot 3.2.2、MyBatis Plus、Flyway | `8080`（Compose 内部） | 身份、会话、配置、编排、资源安装、记忆、用量和统一 API |
| `genie-tool` | Python 3.11、FastAPI、LiteLLM、Pandas、SciPy | `1601` | 搜索、报告、代码、文件、数据分析和表格相关工具 |
| `genie-client` | Python 3.10+、FastAPI、MCP SDK | `8188`（Compose 内部） | MCP Server 连通性、工具发现和调用 |
| `mysql` | MySQL 8.0 | `3306`（Compose 内部） | 用户、Session、会话、Agent、Team、Skill、MCP 和用量数据 |

正式 Compose 默认只向宿主机暴露 UI 的 `3000` 端口；本地开发覆盖文件会额外暴露 `genie-tool:1601`，便于调试文件和工具接口。

### 三种执行模式

- **Auto**：系统主控根据问题、历史和在线资源，选择一个最合适的 Agent 或一个 Team；选定后由目标资源接管执行。
- **Solo**：用户明确指定一个在线 Agent，适合边界清晰、强调稳定人设或固定工具集的任务。
- **Ensemble**：用户指定 Team 或一组 Agent，由编排器生成计划；无依赖的子任务可并行，有依赖的步骤按顺序执行，最后统一汇总。

### 文件与工作区边界

聊天附件与浏览器工作区是两条独立的数据链路：

- 聊天附件由服务端保存，并绑定到当前用户和会话。
- 浏览器工作区文件主要保存在当前浏览器的 IndexedDB 中；工作区目录保存在 localStorage 中。
- 工作区内容按用户、工作区和会话作用域隔离，不会自动跨浏览器或跨设备同步。
- `genie-tool` 或 Agent 生成的远端文件会先作为可导入文件显示，用户导入后才进入本地工作区。
- 浏览器 Python 只能接触明确放入执行上下文的文件索引和内容，不能任意读取其他工作区或浏览器凭据。

### 浏览器执行沙箱

浏览器 Skill 会先校验 ZIP、Manifest、入口文件、相对路径和文件大小，再加载到独立的 Pyodide Web Worker 中执行。沙箱只挂载当前工作区明确授权的文件，单次最多加载 32 个、总计 50 MiB；输出文件同样经过名称、路径、数量和大小校验后才会写回工作区。执行超时、用户取消或 Worker 崩溃时，运行实例会被终止，并在下一次任务中重新创建。

该机制提供的是浏览器进程内的代码与工作区作用域隔离，不等同于虚拟机或容器级安全边界；网络能力仍受浏览器和 CORS 策略约束，因此只应安装和运行可信 Skill。

## 项目结构

```text
.
├── ui/                       # React 前端、工作区、资源广场、编排时间线
├── genie-backend/            # Spring Boot 平台后端与原 JoyAgent 智能体内核
├── genie-tool/               # FastAPI 工具服务：搜索、报告、代码、数据分析
├── genie-client/             # FastAPI MCP 客户端服务
├── skills/                   # 运行时 Skill Package 根目录
├── deploy/                   # Docker Compose 与本地热更新配置
├── scripts/acceptance/       # MVP / Phase2 分层验收脚本
├── docs/mvp-contract/        # REST、SSE、Snapshot 与编排契约
├── data/memory/              # Docker 本地用户记忆挂载目录
├── .env.example              # 无密钥的环境变量模板
├── Makefile                  # 单元、集成、UI、E2E 与契约验收入口
└── Dockerfile                # 原一体化镜像构建方式
```

## 使用环境

### 推荐方式：Docker Compose

宿主机只需要：

- Git，并已获得本私有仓库的访问权限
- Docker Desktop 或 Docker Engine
- Docker Compose v2（使用 `docker compose` 命令）
- 可访问所选模型 API；使用 Deep Search 时还需可访问配置的搜索服务

Compose 会在容器内使用以下运行时：

| 组件 | 版本/要求 |
| --- | --- |
| Java | 17 |
| Maven | 3.9.9（Compose 开发栈） |
| Node.js | 20 |
| pnpm | 9.15.0 |
| Python | 3.11（`genie-client` 代码兼容 3.10–3.13） |
| uv | 0.6.14（Compose 开发栈） |
| MySQL | 8.0 |

### 手动开发环境

如果不使用 Docker，需要自行准备 JDK 17、Maven、Node.js 20、pnpm 9.15、Python 3.11、uv、MySQL 8.0，以及 Bash 环境。Windows 建议使用 Git Bash 或 WSL 执行 Makefile 和验收脚本。

## 快速开始

### 1. 克隆私有仓库

先在 GitHub 配置有权限的 SSH Key、PAT 或 Git Credential Manager，然后执行：

```bash
git clone https://github.com/dddd-cloud/genie-Multiagent-platform.git
cd genie-Multiagent-platform
```

### 2. 创建本地配置

PowerShell：

```powershell
Copy-Item .env.example .env
```

Bash：

```bash
cp .env.example .env
```

至少替换 `.env` 中的以下值，禁止把真实密钥提交到 Git：

```dotenv
GENIE_DB_USERNAME=genie
GENIE_DB_PASSWORD=<your-mysql-password>
GENIE_BOOTSTRAP_ADMIN_USERNAME=admin
GENIE_BOOTSTRAP_ADMIN_PASSWORD=<your-admin-password>
GENIE_INTERNAL_AGENT_TOKEN=<your-internal-agent-token>
MVP_ACCEPTANCE_USER_PASSWORD=<your-local-acceptance-user-password>
MVP_ACCEPTANCE_ADMIN_PASSWORD=<your-local-acceptance-admin-password>
```

要启用完整的模型、Agent、Team、Skill 与 MCP 能力，还需要：

```dotenv
SPRING_PROFILES_ACTIVE=local
VITE_PHASE2_ENABLED=true
GENIE_MCP_CREDENTIAL_KEY=<base64-encoded-32-byte-key>
GENIE_INTERNAL_MCP_TOKEN=<your-internal-mcp-token>

DEFAULT_MODEL=<your-model-name>
OPENAI_BASE_URL=<openai-compatible-base-url>
OPENAI_API_KEY=<your-api-key>
```

可使用下面的 PowerShell 生成 32 字节随机 MCP 加密密钥：

```powershell
$key = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($key)
[Convert]::ToBase64String($key)
```

Deep Search 为可选能力，可按需设置：

```dotenv
USE_SEARCH_ENGINE=serp
SERPER_SEARCH_URL=https://google.serper.dev/search
SERPER_SEARCH_API_KEY=<your-serper-api-key>
```

### 3. 启动真实模型开发栈

PowerShell：

```powershell
docker compose --env-file .env `
  -f deploy/docker-compose.mvp.yml `
  -f deploy/docker-compose.local.override.yml `
  -f deploy/docker-compose.real-model.override.yml config --quiet

docker compose --env-file .env `
  -f deploy/docker-compose.mvp.yml `
  -f deploy/docker-compose.local.override.yml `
  -f deploy/docker-compose.real-model.override.yml up -d
```

Bash：

```bash
docker compose --env-file .env \
  -f deploy/docker-compose.mvp.yml \
  -f deploy/docker-compose.local.override.yml \
  -f deploy/docker-compose.real-model.override.yml config --quiet

docker compose --env-file .env \
  -f deploy/docker-compose.mvp.yml \
  -f deploy/docker-compose.local.override.yml \
  -f deploy/docker-compose.real-model.override.yml up -d
```

首次启动会安装依赖并构建前后端，耗时取决于网络和本地缓存。服务就绪后访问：

- 产品入口：<http://localhost:3000/app>
- 根地址：<http://localhost:3000>（自动跳转）
- 本地工具 OpenAPI：<http://localhost:1601/docs>

使用 `.env` 中的 `GENIE_BOOTSTRAP_ADMIN_USERNAME` 和 `GENIE_BOOTSTRAP_ADMIN_PASSWORD` 登录。管理员账号只在数据库首次初始化时引导创建；数据库已有数据后，修改 `.env` 不会自动修改该账号密码。

### 4. 无真实模型的 Fake Agent 验收

如只验证登录、会话、SSE、持久化和基础页面，可将：

```dotenv
SPRING_PROFILES_ACTIVE=mvp-acceptance
```

然后不叠加 real-model override：

```powershell
docker compose --env-file .env `
  -f deploy/docker-compose.mvp.yml `
  -f deploy/docker-compose.local.override.yml up -d
```

Fake Agent 不能代表真实模型、MCP、Skill 和多专家编排已经可用，只适合基础链路验收。

### 5. 查看状态与停止

```powershell
docker compose --env-file .env `
  -f deploy/docker-compose.mvp.yml `
  -f deploy/docker-compose.local.override.yml ps

docker compose --env-file .env `
  -f deploy/docker-compose.mvp.yml `
  -f deploy/docker-compose.local.override.yml logs -f

docker compose --env-file .env `
  -f deploy/docker-compose.mvp.yml `
  -f deploy/docker-compose.local.override.yml stop
```

`stop` 会保留 MySQL、Maven、pnpm、uv 和 Python 虚拟环境卷，方便下次快速启动。

## 基本使用

1. 使用管理员账号登录，并在“用户管理”中创建其他用户。
2. 在设置中添加或确认一个可用的 OpenAI-compatible 模型。
3. 从“资源广场”安装专家、专家团队、Skill 或 MCP，或在设置中自行创建。
4. 回到新会话，在输入框下方选择 `Auto`、`Solo` 或 `Ensemble`。
5. 如需处理本地数据，进入“工作区”，创建工作区并上传文件，再在该工作区内发起会话。
6. 观察实时编排时间线；任务结束后检查最终回答、专家步骤、工具状态和生成文件。
7. 管理员可以在“用量”页面查看模型调用汇总和用户用量。

## 配置说明

完整模板见 [.env.example](./.env.example)，本地部署说明见 [deploy/README.local.md](./deploy/README.local.md)。常用配置分为：

| 类别 | 变量 |
| --- | --- |
| 数据库与登录 | `GENIE_DB_*`、`GENIE_BOOTSTRAP_ADMIN_*`、`GENIE_SESSION_TIMEOUT` |
| 内部服务安全 | `GENIE_INTERNAL_AGENT_TOKEN`、`GENIE_INTERNAL_MCP_TOKEN` |
| MCP 凭据 | `GENIE_MCP_CREDENTIAL_KEY` |
| 模型 | `DEFAULT_MODEL`、`OPENAI_BASE_URL`、`OPENAI_API_KEY`、`GENIE_TITLE_MODEL` |
| 搜索 | `USE_SEARCH_ENGINE`、`SERPER_SEARCH_URL`、`SERPER_SEARCH_API_KEY` |
| 记忆与上下文 | `GENIE_MEMORY_DIR`、`GENIE_HISTORY_MAX_TURNS`、`GENIE_HISTORY_MAX_CHARACTERS` |
| SSE 与快照 | `GENIE_SSE_TIMEOUT_MILLIS`、`GENIE_STREAM_SNAPSHOT_MAX_BYTES` |
| 前端功能 | `VITE_PHASE2_ENABLED`、`VITE_PYODIDE_INDEX_URL`、`FILE_SERVER_URL` |

`VITE_*` 变量会在前端构建时写入产物。修改后需要重新执行前端构建，而不是只重启浏览器。

## 开发与测试

### 常用命令

后端：

```bash
cd genie-backend
mvn test
```

前端：

```bash
cd ui
pnpm install --frozen-lockfile
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

Python 服务：

```bash
cd genie-tool
uv sync --frozen
uv run python server.py
```

### 统一验收入口

```bash
make mvp-unit
make mvp-ui
make mvp-acceptance
make phase2-005-acceptance
```

验收体系包括 Java 单元/集成测试、Testcontainers MySQL、前端 Vitest、Playwright E2E、JSON Schema 契约校验和真实服务脚本。详细契约见 [docs/mvp-contract](./docs/mvp-contract/)。

### 本地快速更新

Docker 本地栈启动后，普通 Java 修改可执行：

```powershell
.\deploy\reload-backend.ps1
```

前端修改可在 UI 容器中重新构建：

```powershell
docker exec joyagent-mvp-ui-1 bash -lc "cd /workspace/ui && pnpm build"
```

修改 `pom.xml` 或新增 Maven 依赖时，需要设置 `GENIE_BACKEND_REPACKAGE=1` 后重新创建后端容器。更多说明见 [deploy/README.local.md](./deploy/README.local.md)。

## 安全与数据说明

- 不要提交 `.env`、模型 API Key、MCP Token、数据库密码或本地运行数据。
- MCP 凭据使用 32 字节密钥加密保存；生产环境必须使用稳定、安全备份且不入库的密钥。
- Agent、Team、Skill、MCP、会话、附件、记忆和模型配置均应通过当前用户作用域访问。
- 安装外部 Skill 或连接 MCP 前，应核对来源、权限、网络访问范围和数据处理方式。
- 浏览器 Pyodide Skill 运行在前端 Worker 中，但仍应只安装可信 Skill，并限制输入文件和输出大小。
- 浏览器工作区默认不跨设备同步；清理浏览器站点数据前请先导出重要文件。
- 正式环境建议由反向代理统一提供 HTTPS，不要直接暴露 MySQL、后端或内部 MCP Client 端口。

## 已知边界与建议路线

- [ ] 补充真实案例视频、截图和可公开演示数据。
- [ ] 为浏览器工作区增加可选的服务端同步、备份和跨设备恢复。
- [ ] 完善更多 MCP Transport 和 OAuth/凭据生命周期管理。
- [ ] 增加生产镜像、反向代理、监控、告警、限流和备份恢复手册。
- [ ] 建立上游 JoyAgent 定期同步与冲突审计流程。

## 文档索引

- [本地 Docker 部署](./deploy/README.local.md)
- [传统手动部署说明](./Deploy.md)
- [MVP REST API 契约](./docs/mvp-contract/rest-api-v1.md)
- [流式快照协议](./docs/mvp-contract/snapshot-v1.md)
- [配置契约](./docs/mvp-contract/configuration.md)
- [错误码](./docs/mvp-contract/error-codes.md)
- [Phase2 契约说明](./docs/mvp-contract/phase2/README.md)

## 贡献

本仓库为私有项目。提交改动前建议：

1. 从当前主线创建功能分支。
2. 不提交密钥、本地卷、构建产物和测试证据中的敏感数据。
3. 完成与改动范围匹配的单元测试、类型检查和验收脚本。
4. 在 Pull Request 中说明用户影响、技术实现、配置变化、数据库迁移和回滚方式。
5. 涉及上游代码时保留原版权、许可证和第三方 Notice。

## 上游致谢与许可

本项目基于京东开源的 [JoyAgent-JDGenie](https://github.com/jd-opensource/joyagent-jdgenie) 进行二次开发。感谢原项目团队提供多智能体框架、工具服务、前端产品和相关开源成果。

项目沿用 Apache License 2.0。使用、分发或二次开发时，请同时遵守 [LICENSE](./LICENSE) 与 [NOTICE-Third Party](./NOTICE-Third%20Party) 中的条款和第三方许可要求。
