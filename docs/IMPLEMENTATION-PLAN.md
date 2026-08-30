# V1 实施顺序

本计划把已确认的设计决策转换为可执行的垂直切片。每个阶段完成后单独提交代码、测试、数据库迁移和必要文档，并通过确定性 CI；CI 不调用真实 Gemini Developer API。

## 仓库边界

```text
/
├─ backend/       # 一个 Maven Spring Boot 项目
├─ frontend/      # Vue 3 + Vite
├─ docs/          # 范围、契约、评测和 ADR
├─ infra/         # Docker Compose、Caddy 和部署脚本
└─ fixtures/      # 模拟案例、知识文档和确定性种子
```

## 垂直切片

1. **项目骨架与数据库**：建立 Maven/Vite 工程、Flyway、PostgreSQL Testcontainers、健康端点和基础 CI。
2. **模拟器与状态机**：建立升级任务、设备、日志、版本规则和诊断任务状态；完成持久化、乐观锁与恢复边界。
3. **版本不兼容只读切片**：完成工具查询、权威规则、证据引用、结构化报告和禁止重试的只读闭环。
4. **回调超时完整闭环**：完成计划、15分钟批准、幂等执行、验证、审计和并发冲突处理。
5. **知识检索**：加入8篇 Markdown 知识、Front Matter、ONNX Embedding、内容哈希索引、元数据过滤和 Top‑4 检索。
6. **Vue 运维工作台**：完成案例入口、诊断详情、报告与批准、审计时间线，以及轮询、错误码和分级展示。
7. **真实模型评测**：接入 `GEMINI_API_KEY`，对固定的 `gemini-2.5-flash` 基线按 [真实模型评测说明](./REAL-MODEL-EVALUATION.md) 执行回归。
8. **交付与演示**：完成 Docker Compose、Caddy HTTPS、公开只读演示、认证交互演示、README 和一分钟视频；两个本地切片完成后再选择通用云 VM。

## 暂不提前决定

- 具体云厂商、地域、套餐和域名供应商。
- Spring Boot、Spring AI、Vue 和 Node 的具体小版本。
- 是否在 V1 之后引入 Prometheus、Sentry、托管数据库或多服务部署。
