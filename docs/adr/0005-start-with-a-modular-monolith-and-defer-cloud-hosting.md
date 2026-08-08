# 第一版采用模块化单体并延后云端部署

第一版使用一个 Spring Boot 模块化单体承载 Agent、升级任务、模拟数据、知识检索、审批和审计能力；开发时优先在本地运行，云主机只在需要在线演示时再购买和部署。这个选择适应 16GB 本地内存限制，避免在 Agent 闭环尚未验证前引入多服务、消息队列和云基础设施，同时保留后续拆分与上云的接口边界。

代码使用单个 Maven 工程，按 `simulator`、`diagnosis`、`knowledge`、`action`、`identity` 和 `audit` 业务包组织，并通过架构测试约束模块依赖；第一版不使用 Maven 多模块。

## Considered Options

- 一开始拆成多个微服务并购买云资源
- 本地模块化单体，后续按需上云（选择）
- 依赖本地大模型和完整基础设施栈

## Consequences

- 第一版不把 Kafka、ES、Kubernetes 或 GPU 作为前置依赖。
- 模型推理优先使用云端 API，本地保留轻量数据和应用运行环境。
- 本地开发让 PostgreSQL 运行在 Docker 中，Spring Boot 和 Vue 使用本机进程，并将 Docker/WSL 内存限制在约 6GB；交付前再验证完整 Docker Compose。
- 两个本地纵向切片完成后才购买云资源；在线演示先使用 Linux、2 核 4GB、约 40GB 磁盘的轻量云主机，镜像在 CI 中构建，只有监控到真实资源瓶颈后才升配。
- 云端由 Caddy、Spring Boot 和 PostgreSQL 同机部署，不在 V1 引入多主机编排。
- 领域包不能通过共享数据库任意跨模块访问数据，应通过明确接口协作。
