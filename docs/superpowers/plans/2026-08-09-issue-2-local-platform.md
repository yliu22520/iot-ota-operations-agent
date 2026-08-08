# Issue #2 本地平台与模拟升级任务实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付可启动的 Spring Boot、Vue 和 PostgreSQL/pgvector 本地平台，使用确定性模拟数据支持演示运维账号登录、受保护的失败升级任务列表和单任务详情。

**Architecture:** 使用单个 Maven Spring Boot 模块化单体，按 `simulator`、`diagnosis`、`knowledge`、`action`、`identity` 和 `audit` 包划分边界。Flyway 是数据库 Schema 唯一来源，PostgreSQL/pgvector 保存模拟业务、身份和后续工作流基础表；Vue 通过同源 API 与服务端 Session 协作。

**Tech Stack:** Java 17, Spring Boot 3.4.x, Spring Security Session, Spring Data JPA, Flyway, PostgreSQL 16/pgvector, Testcontainers, ArchUnit, Vue 3, TypeScript, Vite, Pinia, Axios, Element Plus, Vitest。

> **实施状态：** 工程、接口、模拟数据、工作台、容器配置和本地可运行验证已完成；Testcontainers 集成测试已接入，但当前机器因 Docker Desktop 守护进程不可用而无法启动容器。

---

### Task 1: 建立可构建的工程边界

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/yliu22520/iotota/IotOtaOperationsApplication.java`
- Create: `backend/src/main/java/com/yliu22520/iotota/{simulator,diagnosis,knowledge,action,identity,audit}/package-info.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `frontend/package.json`, `frontend/tsconfig.json`, `frontend/vite.config.ts`, `frontend/index.html`
- Create: `infra/docker-compose.yml`, `backend/Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`
- Create: `.gitignore`, `README.md`
- Test: `backend/src/test/java/com/yliu22520/iotota/ArchitectureTest.java`

- [ ] **Step 1: Write the failing architecture test**

  让 ArchUnit 导入 `com.yliu22520.iotota`，验证 `simulator` 不依赖 `diagnosis/knowledge/action/identity/audit`，`identity` 不依赖 `action`，并验证六个业务包存在。

- [ ] **Step 2: Run the architecture test to verify it fails**

  Run: `mvn -f backend/pom.xml -Dtest=ArchitectureTest test`

  Expected: FAIL because the Maven project and package markers do not exist。

- [ ] **Step 3: Add minimal Maven/Vue/Compose skeleton**

  Maven 使用 Spring Boot Web、Security、Data JPA、Validation、Actuator、Flyway、PostgreSQL、ArchUnit 和 Testcontainers；不加入 H2 或 DeepSeek 必需依赖。Compose 使用 `pgvector/pgvector:pg16`、后端 8080 和前端 80，并给三个服务设置合计约 3GB 的开发资源上限。

- [ ] **Step 4: Run the architecture test to verify it passes**

  Run: `mvn -f backend/pom.xml -Dtest=ArchitectureTest test`

  Expected: PASS。

### Task 2: 以 Testcontainers 验收接缝定义数据库、种子和身份行为

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__base_schema.sql`
- Create: `backend/src/test/java/com/yliu22520/iotota/ApplicationIT.java`
- Modify: `backend/pom.xml`

- [ ] **Step 1: Write the failing integration tests**

  使用 `pgvector/pgvector:pg16` Testcontainer 和 `@DynamicPropertySource`，通过真实 Spring 应用验证：Flyway 建立 `device`、`firmware_version`、`upgrade_task`、`failure_log`、`message_state`、`operator_user`、`diagnostic_task`、`diagnostic_report`、`retry_plan`、`retry_execution`、`knowledge_document`、`audit_event` 表；启动种子包含 `VERSION_INCOMPATIBLE` 与 `CALLBACK_TIMEOUT` 两个最终失败任务；未登录访问返回 401；带 CSRF 的登录创建 Session；登录后能查询两个模拟失败任务并取得详情。

- [ ] **Step 2: Run the integration tests to verify they fail**

  Run: `mvn -f backend/pom.xml -Pintegration -DskipUnitTests=false verify`

  Expected: FAIL because application services, migrations and API endpoints are not implemented；若 Docker 未运行，先记录 Testcontainers 的环境阻塞，不改变测试接缝。

- [ ] **Step 3: Add the PostgreSQL/pgvector Flyway migration**

  迁移创建 `vector` 扩展、所有当前和后续工作流表、必要外键/唯一约束/索引和 `timestamptz` 字段；Hibernate 配置为 `validate`。不在迁移中写随机或环境相关数据，模拟种子由应用以固定 UUID 和幂等 upsert 写入。

- [ ] **Step 4: Implement deterministic seed and test data access through public APIs**

  种子创建两个设备、两个固件版本、两条最终失败升级任务、版本兼容矩阵、故障日志和消息状态；通过 `SimulatorService` 返回 DTO，不让 Controller 直接访问另一个模块的 Repository。

- [ ] **Step 5: Implement Session identity and protected task APIs**

  使用 Spring Security `CookieCsrfTokenRepository`、服务端 Session 和 BCrypt；启动时以 `DEMO_OPERATOR_USERNAME`/`DEMO_OPERATOR_PASSWORD` 初始化演示运维账号。提供 `/api/v1/auth/csrf`、`/api/v1/auth/login`、`/api/v1/auth/me`、`/api/v1/auth/logout`、`GET /api/v1/upgrade-tasks` 和 `GET /api/v1/upgrade-tasks/{id}`。健康端点匿名可用但只返回 UP/DOWN；任务接口必须认证。

- [ ] **Step 6: Run the integration tests to verify they pass**

  Run: `mvn -f backend/pom.xml -Pintegration verify`

  Expected: `ApplicationIT` 全部 PASS，且无 H2 依赖。

### Task 3: 完成 Vue 运维工作台与前端行为测试

**Files:**
- Create: `frontend/src/main.ts`, `frontend/src/App.vue`, `frontend/src/styles.css`
- Create: `frontend/src/router/index.ts`, `frontend/src/stores/auth.ts`, `frontend/src/services/api.ts`
- Create: `frontend/src/views/LoginView.vue`, `frontend/src/views/TaskListView.vue`, `frontend/src/views/TaskDetailView.vue`
- Create: `frontend/src/components/SimulatedDataBanner.vue`, `frontend/src/components/TaskTable.vue`
- Test: `frontend/src/components/SimulatedDataBanner.spec.ts`, `frontend/src/views/TaskListView.spec.ts`

- [ ] **Step 1: Write the failing component tests**

  验证模拟数据 Banner 始终展示“模拟数据”，任务列表渲染任务标识、设备、目标版本、失败时间、状态和诊断状态，并在 API 返回 401 时跳转登录。

- [ ] **Step 2: Run the frontend tests to verify they fail**

  Run: `npm --prefix frontend test -- --run`

  Expected: FAIL because the Vue source and test configuration do not exist。

- [ ] **Step 3: Implement the typed API and workbench views**

  Axios 使用同源 `/api`、Cookie Session 和 CSRF Cookie；Pinia 只保存身份和界面状态。列表只显示最终失败任务，详情展示任务、设备、固件、日志和消息状态，并明确标识模拟器来源；不添加通用聊天或写操作入口。

- [ ] **Step 4: Run frontend tests and production build**

  Run: `npm --prefix frontend test -- --run` and `npm --prefix frontend run build`

  Expected: tests PASS and Vite build succeeds。

### Task 4: 容器化、文档和安全回归

**Files:**
- Modify: `infra/docker-compose.yml`, `README.md`, `.github/workflows/ci.yml`
- Create: `.dockerignore`, `frontend/.dockerignore`, `backend/.dockerignore`

- [ ] **Step 1: Document clean-start commands and demo credentials**

  README 说明 `docker compose -f infra/docker-compose.yml up --build`、本地访问地址、环境变量注入方式、模拟数据边界、无 `DEEPSEEK_API_KEY` 仍可用，以及测试命令；不得包含真实密钥。

- [ ] **Step 2: Add container health and migration ordering**

  PostgreSQL healthcheck 通过后才启动后端，后端健康后再提供前端代理；前端 `/api` 代理到 `backend:8080`。Compose 不暴露 PostgreSQL 公网端口，只暴露前端演示端口。

- [ ] **Step 3: Add CI commands and secret scan assertions**

  CI 运行单元/架构测试、前端测试/build，并提供需要 Docker 的 integration profile；脚本检查仓库文本中不存在 `sk-`、`DEEPSEEK_API_KEY=` 后跟真实值等凭证模式。

- [ ] **Step 4: Run the full locally available verification**

  Run: `mvn -f backend/pom.xml test`, `npm --prefix frontend test -- --run`, `npm --prefix frontend run build`, `git diff --check`。

  Expected: 单元/架构/前端验证 PASS；若 Docker 未启动，单独报告 `mvn -Pintegration verify` 未执行或失败的环境原因。

### Task 5: Review and commit

**Files:** all implementation files above

- [ ] **Step 1: Run code review against the initial commit**

  检查 Issue #2 每条验收标准、模块依赖、认证保护、模拟数据标识、密钥缺失行为和迁移幂等性；修复审查发现的问题后重新运行验证。

- [ ] **Step 2: Commit the completed change**

  Run: `git add . && git commit -m "feat: bootstrap local OTA operations platform"`

  Expected: 当前分支产生一个包含工程、测试、容器和文档的提交。
