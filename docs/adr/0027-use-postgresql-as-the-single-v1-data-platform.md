# 第一版统一使用 PostgreSQL

第一版使用一个 PostgreSQL 实例保存模拟业务数据、诊断任务、结构化报告、批准、执行记录、审计事件和小型知识库，不为不同模块引入独立数据库。需要语义检索时在同一实例中启用 pgvector。由于系统依赖部分唯一索引、`timestamptz` 和向量扩展，集成测试使用 Testcontainers 启动 PostgreSQL/pgvector，不再使用 H2 模拟数据库行为。

## Considered Options

- 每个模块使用独立数据库
- PostgreSQL 加 Elasticsearch 和独立向量数据库
- 单个 PostgreSQL 实例，按模块边界隔离（选择）

## Consequences

- 本地与云端部署的内存和运维成本较低。
- 云端 PostgreSQL 与应用部署在同一台虚拟机且不开放公网端口，V1 不购买托管数据库。
- Flyway SQL 是数据库 Schema 的唯一来源，Hibernate 只执行 `validate`；本地、CI 和云端运行同一组迁移。
- 纯单元测试不连接数据库，数据库集成测试共享受控的 PostgreSQL Testcontainer，以适应 16GB 开发机。
- 业务表通过 Spring Data JPA 访问，知识向量通过 Spring AI `PgVectorStore` 访问；V1 不叠加 MyBatis 或 jOOQ。
- pgvector 列固定为本地多语言 E5 模型的 384 维向量并使用余弦距离；更换 Embedding 模型需要显式迁移和重建索引。
- 表和代码模块必须保持所有权边界，不能因为共用数据库而任意跨模块访问。
- 知识规模增长或检索需求变化后，可以单独迁移检索存储。
