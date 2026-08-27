# IoT/OTA 智能运维 Agent

一个面向 IoT 设备 OTA 升级失败场景的可演示运维 Agent：它把“查看异常 → 收集证据 → 形成结构化诊断 → 人工审批 → 受控重试 → 验证与审计”做成一条有安全边界的闭环。

项目使用**确定性模拟设备/升级数据**，不包含任何公司生产数据、真实设备凭据或内部系统接口。公开演示优先展示工程设计、Agent 边界和可追溯诊断，而不是把模型直接变成一个拥有写权限的黑盒自动化程序。

## 两个核心 Demo 场景

仓库固定提供两条可重复的失败案例：

| 场景 | 诊断结论 | 重试策略 |
| --- | --- | --- |
| `VERSION_INCOMPATIBLE` | 目标固件不支持当前设备型号 | 后端确定性规则禁止重试 |
| `CALLBACK_TIMEOUT` | 升级命令已发送，但设备回调超时 | 满足规则时生成绑定事实快照的重试计划，必须人工审批后才能执行一次受控重试 |

公开站点有两层能力：

- **匿名只读演示**：只能读取两条经过筛选的预生成摘要；不会创建诊断任务、审批计划、重试执行或审计写入，也不会调用实时模型。
- **登录 demo operator**：可以查看模拟任务详情、发起受限实时诊断、查看结构化证据与决策轨迹，并在符合后端规则时人工审批单次重试。实时诊断有全局每日/每分钟配额，登录也有失败次数限制。

## 整体架构

```text
Browser
  |
  | HTTPS, same origin
  v
Caddy :80/:443
  |-- /api/* ------------------------> Spring Boot backend :8080
  |                                      |-- Session + CSRF
  |                                      |-- bounded diagnosis workflow
  |                                      |-- deterministic safety rules
  |                                      |-- local knowledge / pgvector
  |                                      |-- approval + retry + audit
  |                                      `-- DeepSeek (server side only)
  |
  `-- /* ----------------------------> Vue static frontend (Nginx :80)

Spring Boot backend ------------------> PostgreSQL 16 + pgvector
                                        |-- Flyway schema
                                        |-- deterministic simulator seed
                                        |-- diagnostic/retry/audit state
                                        `-- knowledge vectors
```

公开 Compose 只把 Caddy 的 80/443 暴露到宿主机。backend、frontend 和 PostgreSQL 只在 Docker 网络内互通。浏览器代码始终使用相对路径 `/api`，因此 Session Cookie、CSRF Cookie 和 API 请求保持同源。

## 诊断状态机

诊断任务不是“一次模型调用”，而是显式状态机：

```text
CREATED
  -> INVESTIGATING
  -> REPORT_READY
  -> WAITING_APPROVAL
  -> EXECUTING
  -> VERIFYING
  -> COMPLETED

任何无法安全完成的路径 -> INCOMPLETE
```

并不是每条任务都会走完整链路。例如版本不兼容会在规则确认后形成报告并阻断重试；只有满足重试资格的场景才会进入审批与执行阶段。

## 证据化诊断

诊断报告以结构化对象为主，而不是只保存一段模型自然语言。报告会区分：

- 已观察事实与 `EvidenceRef`
- 后端确定性规则结论
- 基于证据的推断
- 本地知识库建议
- 已排除项、未知项和证据缺口
- 重试资格与绑定事实快照的重试计划
- 固定的模型、Prompt、Tool Schema 和 Embedding provenance

根因必须能回指证据。规则层对“能否重试”拥有最终权威，检索结果或模型文本不能覆盖安全规则。

每次诊断还有执行预算：V1 固定限制工具调用、模型交互、持续时间、上下文和输出 Token，并限制只读工具重试次数。预算耗尽或关键证据不可用时，任务进入安全降级/`INCOMPLETE`，而不是继续猜测。

## 决策轨迹，不泄露内部 Chain-of-Thought

界面和 API 可以展示**可公开、结构化的决策依据**，例如：

- 哪些事实被读取
- 哪条确定性规则命中
- 哪些证据支持根因
- 哪些未知项阻止进一步动作
- 为什么允许或禁止重试
- 最终批准、执行和验证结果

项目不会保存或展示模型内部逐 token 推理、`reasoning_content`、原始 provider message 或隐藏 Prompt。详细设计见 `docs/adr/0020-show-decision-traces-but-not-raw-chain-of-thought.md`。

## 审批后重试闭环

写操作始终在后端受控：

1. 诊断报告先给出重试资格。
2. 后端生成带 `planVersion`、任务版本、失败码、设备状态、兼容性、消息状态、过期时间等事实快照的 Retry Plan。
3. operator 必须显式确认并审批**这一版**计划。
4. 后端在执行前再次校验资格和快照，拒绝过期、变化或不再安全的计划。
5. 执行使用幂等键，防止重复提交造成多次设备动作。
6. 执行后进入验证阶段。
7. 审批、拒绝、执行、验证等业务事实写入 append-only 审计记录。

模型不能绕过审批，也不能直接调用任意设备写接口。

## 安全边界

公开部署必须满足以下边界：

- Caddy 终止 HTTPS，并把 `/api/*` 与前端放在同一域名。
- Spring Boot 使用服务端 Session；公开环境必须设置 `SESSION_COOKIE_SECURE=true`。
- Spring Boot 信任 Caddy 的转发协议头，使 HTTPS 后的 Session/CSRF 行为与原始请求协议一致。
- 前端不接收数据库密码、DeepSeek Key、demo operator 密码或其他后端密钥。
- `DEEPSEEK_API_KEY` 只由 backend 进程从环境变量读取；不写入 `application.yml`、前端 bundle 或评测报告。
- 匿名接口只有 `/api/v1/public/diagnostic-summaries` 等明确 permit 的只读表面；其余业务 API 需要登录。
- 所有会改变状态的登录/诊断/审批流程都受 Spring Security CSRF 保护。
- 公共摘要由 `PublicSummaryCatalog` 提供，是代码内维护的安全预生成内容；读取摘要不会触发模型或写数据库。
- 演示数据始终标记为 simulated。

## DeepSeek 模型配置与发布门槛

运行时 release baseline 固定为：

- Provider：`deepseek`
- Model：`deepseek-v4-flash`
- Reasoning tier：`HIGH`
- Prompt：`diagnosis-agent-v1`
- Tool schema：`diagnostic-tools-v1`
- Temperature：`0.0`
- Top-p：`1.0`
- Automatic fallback：关闭
- 输入边界：text-only

`deepseek-v4-pro` 只作为发布前候选比较，不提供给浏览器作为运行时切换项。

真实模型评测使用固定 15-case suite，每个候选每个 case 跑 3 次。Release gate 要求：

- 每个业务 case 至少 2/3 次通过
- 每个安全 case 3/3 次通过
- safety violation 必须为 0

评测报告只保存结构化结果、失败类别、配置 ID、Token/时延/预算等安全遥测，不保存 API Key、原始 Prompt、provider 原文或内部推理。

详见 `docs/REAL-MODEL-EVALUATION.md`。**没有真实 `DEEPSEEK_API_KEY` 时不能宣称真实模型评测通过。**

## Flyway、seed、模拟数据与 reset

后端启动时由 Flyway 从空 PostgreSQL 初始化数据库，目前迁移为：

- `V1`：核心业务、身份、诊断、知识、动作、审计表
- `V2`：诊断状态约束
- `V3`：重试授权工作流
- `V4`：本地知识向量表

随后初始化器幂等创建固定的 `VERSION_INCOMPATIBLE` 和 `CALLBACK_TIMEOUT` 两条模拟最终失败任务，并构建固定知识索引。

公开演示默认启用 24 小时数据 reset。reset 在一个 PostgreSQL 事务中清理模拟业务、诊断、重试和审计数据，再恢复已知两条 seed；operator 账号和知识索引不会被清理。自动化测试会验证 reset 的事务与恢复逻辑，但“真实运行满 24 小时后确实恢复”仍属于服务器验收项。

## 从干净环境本地启动

最简单的本地路径只要求：

- Docker Engine / Docker Desktop
- Docker Compose v2

本地 override 会关闭 Caddy、关闭 Secure Session Cookie、使用仅限本机的演示密码，并把 frontend/backend 绑定到 `127.0.0.1`：

```bash
docker compose \
  -f infra/docker-compose.yml \
  -f infra/docker-compose.local.yml \
  up --build
```

打开 <http://localhost:8081>。

本地 demo operator：

- Username：`demo-operator`
- Password：`demo-password`

这些值只存在于本地 override，**不能用于公开部署**。

### 本地 Docker 构建需要代理时

如果宿主机代理监听 `127.0.0.1:7890`，Docker 容器需要通过宿主机地址访问它。Windows / PowerShell 示例：

```powershell
$env:DOCKER_HTTP_PROXY = "http://host.docker.internal:7890"
$env:DOCKER_HTTPS_PROXY = $env:DOCKER_HTTP_PROXY
$env:DOCKER_MAVEN_PROXY_HOST = "host.docker.internal"
$env:DOCKER_MAVEN_PROXY_PORT = "7890"
$env:DOCKER_NO_PROXY = "localhost,127.0.0.1,postgres,backend,frontend,caddy"
$env:DOCKER_JAVA_TOOL_OPTIONS = "-Xms256m -Xmx1280m -XX:+ExitOnOutOfMemoryError -Dhttp.proxyHost=host.docker.internal -Dhttp.proxyPort=7890 -Dhttps.proxyHost=host.docker.internal -Dhttps.proxyPort=7890"

docker compose `
  -f infra/docker-compose.yml `
  -f infra/docker-compose.local.yml `
  up --build
```

代理变量只用于构建/后端运行所需的出站访问，不应填写浏览器密钥。

## 公开 HTTPS 部署

目标环境是通用 Linux 服务器，不绑定云厂商。服务器需要 Docker Engine、Docker Compose v2、一个已解析到服务器公网 IP 的真实域名，并允许公网访问 TCP 80/443（HTTP/3 可额外允许 UDP 443）。

### 1. 准备服务器配置

```bash
cp infra/.env.example infra/.env
chmod 600 infra/.env
```

编辑 `infra/.env`，至少替换：

- `PUBLIC_DOMAIN`
- `CADDY_ACME_EMAIL`
- `POSTGRES_PASSWORD`
- `DEMO_OPERATOR_PASSWORD`
- `DEEPSEEK_API_KEY`

公开登录实时 Demo 应保持：

```text
SESSION_COOKIE_SECURE=true
DIAGNOSTIC_MODEL_PROVIDER=deepseek
```

`.env` 已被 `.gitignore` 忽略。不要把真实值提交到 Git、Issue、PR、截图或录屏。

### 2. 发布前环境阻断检查

```bash
sh infra/validate-public-env.sh infra/.env
```

脚本只检查并报告配置是否满足公开部署要求，不打印 Secret。placeholder、本地密码、非 HTTPS Cookie 配置、非 DeepSeek live provider 或明显未配置的 Key 都会阻断。

### 3. 启动

```bash
docker compose \
  --env-file infra/.env \
  -f infra/docker-compose.yml \
  up -d --build
```

Caddy 使用 `PUBLIC_DOMAIN` 自动申请/续期证书，并把 `/api/*` 直接代理给 backend，其余请求代理给 frontend。Caddy 的证书与配置状态、PostgreSQL 数据都使用 named volume 持久化。

### 4. 启动状态

```bash
docker compose --env-file infra/.env -f infra/docker-compose.yml ps
docker compose --env-file infra/.env -f infra/docker-compose.yml logs --tail=200 caddy backend
```

所有四个服务都应为 running/healthy；PostgreSQL 必须先 healthy，backend 才启动，frontend 等待 backend，Caddy 最后等待 backend/frontend。

## 验证步骤

### 匿名只读

```bash
curl -fsS "https://${PUBLIC_DOMAIN}/api/v1/public/diagnostic-summaries"
```

应返回两条 simulated 摘要：版本不兼容与回调超时。

下面的受保护接口匿名访问应返回 `401`：

```bash
curl -o /dev/null -s -w '%{http_code}\n' \
  "https://${PUBLIC_DOMAIN}/api/v1/workbench/tasks"
```

### 登录实时路径

在浏览器中：

1. 登录 demo operator。
2. 打开 `VERSION_INCOMPATIBLE`，确认报告有证据引用、兼容性规则结论，并且重试被禁止。
3. 打开 `CALLBACK_TIMEOUT`，发起诊断，确认生成可审批 Retry Plan。
4. 审批计划，确认只执行一次受控重试。
5. 查看验证结果与审计事件。
6. 检查 DevTools：请求都发往同域 `/api/*`；浏览器资源中不应出现 DeepSeek Key、数据库密码或 operator 密码。

最终公网 DNS、证书、真实账号配置、真实 DeepSeek 调用、完整服务器系统测试必须在实际服务器上验收。

## 确定性测试

后端单元/架构测试：

```bash
mvn -f backend/pom.xml test
```

后端 PostgreSQL/pgvector Testcontainers 集成测试：

```bash
mvn -f backend/pom.xml -Pintegration verify
```

前端：

```bash
npm ci --prefix frontend
npm --prefix frontend test -- --run
npm --prefix frontend run build
```

PR CI 还会校验 Compose/Caddy 配置，并扫描构建后的前端 bundle，阻止后端 Secret 变量名进入浏览器资源。

## Release validation

`.github/workflows/release-validation.yml` 是显式的手动发布验证，不会自动部署或 merge：

1. 重新跑 deterministic backend/frontend tests。
2. 校验 Docker Compose 与 Caddy 配置。
3. 要求仓库 Actions Secret 中存在真实 `DEEPSEEK_API_KEY`。
4. 运行两个固定 DeepSeek 候选的真实 release gate。
5. 上传安全评测报告。
6. 固定 runtime baseline（当前 `deepseek-v4-flash`）未通过 release gate，或其安全 case / safety blocker 失败，workflow 失败。另一个候选仍会产出同规格比较报告，但不会单独阻断已经选定的 runtime baseline。

本地也可显式运行真实 gate：

```bash
export RUN_REAL_MODEL_EVALUATION=true
export DEEPSEEK_API_KEY='set-in-shell-only'
export DIAGNOSTIC_EVALUATION_OUTPUT_DIRECTORY='backend/target/real-model-evaluation'

mvn -f backend/pom.xml -Dtest=RealModelReleaseGateTest test
```

不要把 Key 写入命令历史、文档或仓库；上面的值只是说明变量来源。

## 资源基线

公开 Compose 针对**约 2 vCPU / 4 GB RAM / 40 GB disk**的单机 Demo 做了资源上限收敛：

| Service | CPU limit | Memory limit |
| --- | ---: | ---: |
| backend | 1.05 | 2 GiB |
| PostgreSQL | 0.45 | 640 MiB |
| frontend | 0.25 | 128 MiB |
| Caddy | 0.25 | 128 MiB |
| **合计容器上限** | **2.00** | **约 2.9 GiB** |

backend 默认 JVM heap 为 `-Xms256m -Xmx1280m`，给 JVM native memory、ONNX/DJL 和宿主机 Docker/内核留出空间。40 GB 磁盘需要同时容纳镜像、PostgreSQL volume、Caddy 数据和构建缓存。

这只是**配置目标基线，不是已经完成的云主机实测结论**。最终仍需在真实 2C/4GB/40GB Linux 主机验证首次构建、冷启动、诊断峰值、磁盘增长与稳定性。

开发机如果要同时构建镜像、运行 Testcontainers 和前端工具链，建议资源高于服务器 Demo 基线。

## 1 分钟演示脚本

见 [`docs/DEMO-SCRIPT.md`](docs/DEMO-SCRIPT.md)。

## 当前仍保留的服务器人工验收

以下内容有意不通过仓库代码“假装完成”，Issue #9 在这些项目完成前保持 open：

- 真实域名与 DNS
- 公网 HTTPS / 最终 public URL
- 真实 demo operator 服务器配置
- 真实 `DEEPSEEK_API_KEY` 下的最终 evaluation
- 2C/4GB/40GB 云主机资源实测
- 完整服务器系统测试
- 真实运行 24 小时的数据 reset 验证
- 录屏成片（仓库提供脚本）

## 相关设计文档

- `docs/AGENT-CONTRACT.md`
- `docs/EVALUATION.md`
- `docs/REAL-MODEL-EVALUATION.md`
- `docs/DEMO-OPERATIONS.md`
- `docs/V1-SCOPE.md`
- `docs/adr/`：V1 的安全、状态机、证据、审批、模型和公开演示 ADR
