# 第一版使用服务端 Session 认证

第一版使用 Spring Security 和服务端 Session Cookie，预置一个演示运维账号，不引入 JWT、OAuth 或独立身份服务，也不实现注册、找回密码或账号管理。当前系统是单体 Web 应用且只有一个业务角色，Session 能满足身份、批准和审计要求，同时避免令牌刷新、撤销和泄露处理带来的额外复杂度。

## Considered Options

- 服务端 Session Cookie（选择）
- JWT Access Token 和 Refresh Token
- 第三方 OAuth 登录

## Consequences

- 云端部署由 Caddy 自动提供 HTTPS，Session Cookie 使用 `Secure`、`HttpOnly` 和 `SameSite=Lax`，并设置合理的 Session 过期时间。
- Session 连续 30 分钟无操作或达到 8 小时绝对有效期后失效；应用重启可以使用户重新登录，但不能丢失持久化诊断任务。
- Spring Security 保持 CSRF 防护；生产环境前后端同源且不开放跨域，本地开发使用 Vite Proxy。
- 演示账号密码由部署环境注入并以 BCrypt 哈希保存。
- 登录失败按来源 IP 限制为 10 分钟内最多 5 次，超限后暂停 15 分钟，不使用可能被滥用的账号全局锁定。
- 后续拆分独立客户端或多服务认证时，需要重新评估令牌方案。
- 审批人与审计身份必须来自服务端认证上下文。
