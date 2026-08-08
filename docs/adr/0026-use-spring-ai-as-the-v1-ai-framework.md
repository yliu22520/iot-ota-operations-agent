# 第一版使用 Spring AI

第一版使用 Spring AI 作为 Java AI 框架，不同时引入 LangChain4j。Spring AI 与现有 Spring Boot 技术路线一致，并提供工具调用、手动工具循环、结构化输出、可观测性、Vector Store 和后续 MCP 适配能力，适合实现已经确定的受约束诊断工作流。

## Considered Options

- Spring AI
- LangChain4j
- 同时使用两个框架

## Consequences

- Agent 编排、安全规则和任务持久化仍由项目领域代码负责，不能完全委托给框架默认循环。
- LangChain4j 可以作为后续对比研究对象，但不进入第一版依赖。
- Spring AI 升级需要运行完整故障案例回归。
