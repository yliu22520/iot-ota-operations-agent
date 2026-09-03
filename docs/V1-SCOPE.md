# V1 交付范围

## 时间盒

- 一人开发
- 每周投入 15～20 小时
- 总投入约 100 小时
- 目标是在约六周内形成可写入简历、可运行和可演示的项目
- 仓库采用 `backend/`、`frontend/`、`docs/`、`infra/` 和 `fixtures/` 五个顶层边界；`backend/` 内仍是一个 Maven Spring Boot 项目。

## 评测案例

- 10 个业务故障案例
- 5 个安全与异常处理案例
- 每个案例都具有机器可校验的预期结果
- 真实模型回归中每个案例运行 3 次；安全案例必须 3 次全部无违规，业务案例至少 2 次完整通过，任何一次错误写操作都直接阻断发布。
- 模型通过安全门槛后，按结构化输出 20%、故障原因 30%、证据与引用 25%、工具纪律 15%、延迟和成本 10% 比较。
- 每次真实模型回归生成不含 `reasoning_content` 的 JSON 与 Markdown 评测产物。
- 具体案例与断言见 [评测计划](./REAL-MODEL-EVALUATION.md)。
- 扩展到 30 个案例属于 V1 之后的目标

## 用户界面

Vue 3、TypeScript 和 Vite 提供四个核心视图：

1. 故障案例入口
2. 诊断任务详情
3. 结构化报告与审批
4. 审计时间线

通用管理后台不在 V1 范围内。

- 前端使用 Vue Router、Pinia、Axios 和 Element Plus，不引入第二套组件库。
- Pinia 只保存登录身份和界面状态；诊断任务、报告和审计数据由类型化 API Service 与轮询 Composable 管理。
- Springdoc 生成 OpenAPI，使用 `openapi-typescript` 生成 TypeScript 类型，CI 检查生成结果是否过期。
- 使用 Vitest 与 Vue Testing Library 测试关键组件，使用 Playwright 覆盖只读诊断和批准后自动执行两条流程。
- 桌面优先，最低适配宽度约 1024px；移动端保证报告可读，不专门优化批准操作；V1 不做暗色模式和国际化。

- 运维诊断详情固定展示当前阶段、诊断报告、证据、重试计划、决策轨迹和审计时间线六个区域；开发遥测使用独立折叠面板，公开视图只展示报告摘要与关键证据。
- 批准弹窗必须展示升级任务、设备、目标版本、诊断原因、关键证据、影响范围、执行前提、计划版本和 15 分钟倒计时，用户勾选明确授权后方可提交。
- 批准有效计划后后端立即进入 `EXECUTING` 并自动发起幂等重试，不提供第二个手工执行按钮。
- 收到 `APPROVAL_EXPIRED`、`PLAN_VERSION_CONFLICT` 或任务状态变化时，前端关闭原批准弹窗、刷新报告并要求重新生成计划，不允许原地重试陈旧授权。

## 知识检索

- PostgreSQL 与 pgvector
- 首批固定 8 篇资料：OTA 状态、版本兼容、回调超时、下载与校验、设备离线与资源不足、重试安全策略，以及 2 篇历史故障案例。
- 每篇 Markdown 使用 Front Matter 声明 `documentId`、版本、类型、状态、适用组件和更新时间，只有 `ACTIVE` 文档进入索引。
- 使用 Spring AI 本地 ONNX `intfloat/multilingual-e5-small` 生成 384 维向量，以余弦距离检索；不需要第二个云端 API 密钥、GPU 或 Ollama。
- 固定模型版本和 SHA-256，不把模型文件提交到 Git；本地首次安装下载，Docker 镜像构建阶段下载并缓存，运行时不依赖 Hugging Face 网络。
- 按 Markdown 标题与段落切块，每块约 300～500 Token、重叠约 50 Token，保留文档 ID、标题、章节、版本和内容哈希。
- 先进行元数据过滤，再执行 Top‑4 余弦检索；相似度阈值由 15 个案例校准，报告引用文档、章节和块 ID。
- 应用启动时按内容哈希幂等更新新增或变化的知识文档。
- 知识检索工具只接收故障码、症状、组件和目标版本等结构化字段，由后端构造 E5 查询文本，不接收任意搜索 Prompt。
- V1 不使用 Elasticsearch、混合检索或重排序

## 模型接入

- 首轮真实模型评测使用 Gemini Developer API。
- 评测候选固定为 `gemini-3.1-flash-lite`，使用固定 Prompt、工具和 15 个评测案例执行回归。
- Spring AI 使用原生 `spring-ai-starter-model-google-genai` 接入，不把 Gemini 凭证配置成 OpenAI 凭证。
- API 密钥仅通过服务端环境变量 `GEMINI_API_KEY` 注入，不进入源码、配置文件、文档、日志或版本库。
- 首轮评测使用固定的 provider-neutral reasoning budget（4096 tokens）；任何供应商内部 reasoning content 都不得进入持久化、日志或任何角色可见的输出。
- V1 不提供思考模式前端开关；正式基线固定为 `High`，`Off` 与 `Max` 仅作为评测配置，切换后必须重新运行完整回归。
- V1 不依赖 Beta strict tool calling；所有工具名称、参数结构、业务前提和执行资格均由 Java 后端验证。
- 单次诊断最多进行 8 轮模型交互，单次模型输出最多 4K Token，单次组装上下文最多 32K Token，并继续受 90 秒任务总时限约束。
- 模型输出必须通过诊断报告 Schema 和业务规则校验；校验失败时只允许一次修复请求，再次失败则进入 `INCOMPLETE`，且不得生成或执行重试计划。
- 日志、设备消息和检索文档一律视为不可信证据，其中的文本不能改变系统指令、工具权限或重试资格。
- 正式运行只留存规范化报告、决策轨迹和模型遥测，不保存完整 Prompt、原始回复或 `reasoning_content`；本地评测可以保存脱敏后的 Prompt 与最终回复。
- 最终基线仍由同一套 Prompt、工具和 15 个评测案例的回归结果决定，选定后固定模型版本。

## 完成标准

同时满足以下条件时，V1 才可写入简历：

- Docker Compose 可以一键启动项目
- 15 个评测案例全部通过
- 四项安全门槛零违规
- 诊断、批准、幂等执行、验证和审计形成完整闭环
- README 说明架构、本地启动、模拟数据、安全边界和评测结果
- 提供公开只读演示
- 提供认证后的交互演示
- 提供一分钟演示视频

## 首批纵向切片

1. 版本不兼容：完成工具查询、证据报告和禁止重试的只读闭环。
2. 回传超时但满足重试资格：完成计划、批准、幂等执行、验证和审计闭环。

## 处置工作流边界

- 同一个升级任务同时只允许一个活跃诊断；重复发起时返回已有诊断任务。
- V1 允许同一名已认证运维工程师提出并批准重试计划，并记录身份、批准时间和计划版本。
- 批准有效期为 15 分钟；升级任务状态、目标版本或执行前提发生变化时立即失效。
- `COMPLETED` 表示重试请求已被幂等接受并创建新的升级尝试，不等待设备最终升级成功。
- V1 不提供取消诊断按钮，也不引入 `CANCELLED` 状态。

## 在线演示边界

- 匿名访问只展示预置案例和预生成报告，不调用真实模型。
- 登录后的交互演示只能从预置升级任务或故障案例发起诊断，不提供任意文本聊天入口。
- 全站每天最多进行 20 次真实模型诊断，每分钟最多 3 次；额度耗尽后只展示预生成报告，并记录配额命中事件。
- 交互演示产生的诊断、批准、模拟执行和审计数据保留 24 小时，随后整套演示数据按确定性种子重建。
- 云端仅通过平台 Secret 或环境变量注入 `GEMINI_API_KEY`；密钥不进入 Docker Compose、服务器脚本、镜像或版本库，轮换后通过重启应用生效。

## 运行与部署

- 完成两个本地纵向切片后再购买一个月云服务器，不提前为未验证的在线演示付费。
- 本地开发时 PostgreSQL 运行在 Docker 中，Spring Boot 和 Vue 使用本机进程，Docker/WSL 内存限制在约 6GB；交付前另行验证完整 Docker Compose。
- 初始云主机使用 Linux、2 核 CPU、4GB 内存和约 40GB 磁盘；镜像由 CI 构建，服务器不负责编译。
- Spring Boot、PostgreSQL 和 Caddy 部署在同一台虚拟机；PostgreSQL 不开放公网端口，V1 不购买托管数据库。
- Caddy 使用项目域名自动签发 HTTPS；Session Cookie 使用 `Secure`、`HttpOnly` 和 `SameSite=Lax`。

## 认证安全

- Session 在连续 30 分钟无操作后过期，绝对有效期最长 8 小时；应用重启可以要求重新登录，但持久化诊断任务必须继续保留和恢复。
- Spring Security 保持 CSRF 防护；生产环境由 Caddy 提供前后端同源访问且不开放跨域，本地开发通过 Vite Proxy 调用后端。
- V1 只提供一个预置演示运维账号，不实现注册、找回密码或账号管理；密码由部署环境注入并以 BCrypt 哈希保存。
- 登录失败按来源 IP 限制为 10 分钟内最多 5 次，超限后暂停 15 分钟；不对共享演示账号实施全局锁定。

## 可观测性与恢复

- 匿名健康端点只返回 `UP/DOWN`；数据库、模型配置等详细健康信息只允许认证访问。
- 使用 Spring Boot Actuator、Micrometer 和结构化 JSON 日志，关联 `requestId` 与 `diagnosticTaskId`，记录阶段耗时、模型与工具次数、Token 和配额命中；V1 不部署 Prometheus、Grafana、ELK 或 Sentry。
- 应用重启后，调查阶段的任务由用户主动继续；`WAITING_APPROVAL` 重新校验批准有效期；`EXECUTING` 和 `VERIFYING` 根据幂等记录自动对账，禁止盲目重复执行。
- PostgreSQL 卷承受普通进程重启，但不备份 24 小时临时演示数据；数据库迁移、知识文档、故障案例和确定性种子进入 Git，作为可重建资产。

## API 与一致性

- Agent 工具固定为 `getUpgradeTask`、`getDeviceState`、`getVersionCompatibility`、`getFailureLogs`、`searchKnowledge`、`checkRetryEligibility`、`createRetryPlan`、`executeApprovedRetry` 和 `verifyRetryExecution`。
- `executeApprovedRetry` 是唯一可能触发模拟写操作的工具；模型不能直接调用它，只有批准接口完成身份、计划版本、资格和幂等校验后，工作流服务才能调用。
- PostgreSQL 部分唯一索引保证同一升级任务只能存在一个活跃诊断，诊断任务和升级任务使用版本字段进行乐观锁定；并发重复创建时返回已有诊断任务。
- 重试执行先在短事务中校验批准并抢占唯一执行记录，提交后调用模拟升级服务，再用第二个事务保存结果与审计；数据库事务不跨越服务调用。
- `POST /api/v1/diagnostic-tasks` 立即返回 `202 Accepted` 和任务地址，由单机 `TaskExecutor` 后台执行；Vue 每 2 秒轮询状态，V1 不使用 WebSocket 或 SSE。
- API 使用明确的诊断创建、查询、批准和重试执行端点，不提供通用 `/chat` 或 `/execute`；错误统一使用 Spring `ProblemDetail` 和稳定业务错误码。

## 数据规范与数据库测试

- 业务表使用 Spring Data JPA，知识向量使用 Spring AI `PgVectorStore`；V1 不引入 MyBatis、jOOQ 或额外数据访问框架。
- 集成测试使用 Testcontainers 启动 PostgreSQL/pgvector，纯单元测试不连接数据库；不再使用 H2 模拟 PostgreSQL 行为。
- Flyway SQL 是 Schema 的唯一来源，Hibernate 只执行 `validate`，禁止自动创建或修改数据库对象。
- 诊断任务、升级任务、重试计划和执行记录使用应用生成的 UUID；故障案例与业务错误使用稳定、可读的字符串编码。
- 后端统一使用注入的 `Clock` 和 UTC `Instant`，PostgreSQL 使用 `timestamptz`，Vue 按 `Asia/Shanghai` 展示；批准过期只依据服务端时间。

## Agent 契约

- 诊断报告固定包含诊断阶段、故障编码、结论摘要、证据引用、证据缺口、重试资格、禁止原因、重试计划、下一步动作和决策轨迹引用。
- 每次诊断记录模型 ID、思考配置、Prompt 版本、工具 Schema 版本、Embedding 版本和评测配置版本。
- Java 工作流负责阶段转换、预算、资格、批准和写操作；模型只能选择允许的读取工具、整理证据并草拟报告与重试计划。

## 持续集成

- 每次提交运行确定性单元测试、集成测试、架构测试、业务规则测试和安全测试。
- 每次提交运行前端类型检查、组件测试、OpenAPI 类型生成检查和关键 Playwright 流程。
- 默认 CI 不调用真实云端模型。
- 修改模型、Prompt 或工具描述后，手动运行 15 个真实模型案例。
- 发布前必须通过完整真实模型回归和四项安全门槛。
- 15 个案例至少包含 5 类知识检索断言：正确引用、过期文档排除、无相关资料、检索服务失败和伪造引用拒绝。
- 实施顺序固定为：项目骨架与数据库、模拟器和状态机、版本不兼容只读切片、回调超时完整批准闭环、知识检索、Vue 工作台、真实模型评测、Docker 与云端演示。
- 每个垂直切片单独提交代码、测试、迁移和必要文档；CI 永远不调用真实 Gemini Developer API。
- 云端暂不绑定具体厂商，两个本地纵向切片完成后只比较能运行 Docker 的通用 Linux 2核4GB主机，不使用厂商专属数据库或 AI 服务。

## 明确延后

- 多 Agent
- Kafka、Nacos、Kubernetes 和 Elasticsearch
- 独立 MCP Server
- 复杂 RBAC 或多租户
- 双人批准或职责分离
- 用户主动取消诊断
- 模型自动降级切换
- 大规模知识导入
