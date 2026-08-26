# IoT/OTA 智能运维 Agent

Issue #2 交付的是第一条可运行基础路径：Spring Boot 模块化单体、Vue 运维工作台、PostgreSQL/pgvector 和可重复的 OTA 模拟数据。当前切片只负责浏览模拟失败升级任务，不提前实现 Agent 诊断或重试执行。

## 本地启动

要求：Docker Desktop、Docker Compose、Java 17、Maven 和 Node 22。

```powershell
docker compose -f infra/docker-compose.yml up --build
```

如果 Docker 内的 Maven 或模型下载无法直连，而宿主机代理监听在 `127.0.0.1:7890`，先确认代理软件已开启 Allow LAN，然后在 PowerShell 7 中传入构建代理：

```powershell
$env:DOCKER_HTTP_PROXY = "http://host.docker.internal:7890"
$env:DOCKER_HTTPS_PROXY = $env:DOCKER_HTTP_PROXY
$env:DOCKER_MAVEN_PROXY_HOST = "host.docker.internal"
$env:DOCKER_MAVEN_PROXY_PORT = "7890"
$env:DOCKER_NO_PROXY = "localhost,127.0.0.1,postgres"
$env:DOCKER_JAVA_TOOL_OPTIONS = "-Xmx1536m -Dhttp.proxyHost=host.docker.internal -Dhttp.proxyPort=7890 -Dhttps.proxyHost=host.docker.internal -Dhttps.proxyPort=7890"
docker compose -f infra/docker-compose.yml up --build
```

前五个变量用于镜像构建，`DOCKER_JAVA_TOOL_OPTIONS` 还负责运行时首次下载 DJL native runtime；代理主机和端口不会写入运行时以外的配置。当前配置假设代理不需要用户名和密码；如果代理启用了认证，需要另外配置凭据传递方式。

打开 <http://localhost:8081>，使用演示运维账号登录：

- 账号：`demo-operator`
- 密码：`demo-password`

密码仅为本地演示默认值；部署时通过 `DEMO_OPERATOR_PASSWORD` 注入，后端只将 BCrypt 哈希保存到 PostgreSQL。前端通过同源 Nginx 代理访问 API，Spring Security 使用服务端 Session Cookie 和 CSRF 防护。生产 HTTPS 部署时应设置 `SESSION_COOKIE_SECURE=true`。

## 当前功能

- Flyway 从空 PostgreSQL/pgvector 数据库创建模拟业务、身份、诊断、知识、动作和审计基础表。
- 启动时幂等创建两个固定模拟最终失败案例：`VERSION_INCOMPATIBLE` 和 `CALLBACK_TIMEOUT`。
- 未认证请求不能读取升级任务；登录、查询当前身份和退出均通过服务端 Session 完成。
- 工作台展示失败任务列表和单任务详情，页面始终标识数据来自模拟器。
- 后端包按 `simulator`、`diagnosis`、`knowledge`、`action`、`identity`、`audit` 边界组织，并有 ArchUnit 依赖方向测试。
- 本切片不要求 `DEEPSEEK_API_KEY`，没有密钥时仍可完整启动和浏览；仓库、日志和前端资源不包含真实密钥。

## 测试

不连接数据库的单元/架构测试：

```powershell
mvn -f backend/pom.xml test
```

前端组件测试和构建：

```powershell
npm install --prefix frontend
npm --prefix frontend test -- --run
npm --prefix frontend run build
```

Testcontainers 集成测试使用 `pgvector/pgvector:pg16`，不使用 H2，需要 Docker Desktop 正常运行：

```powershell
mvn -f backend/pom.xml -Pintegration verify
```

## 边界说明

这里的设备、固件、升级任务、日志和消息状态都是项目自建的确定性模拟运维数据，不代表公司系统或真实设备。后续切片才会加入诊断报告、知识检索和人工批准后的单任务重试闭环。
