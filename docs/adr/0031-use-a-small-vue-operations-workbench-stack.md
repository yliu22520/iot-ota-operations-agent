# 使用轻量 Vue 运维工作台技术栈

V1 前端使用 Vue Router、Pinia、Axios 和 Element Plus；Pinia 只保存身份与界面状态，服务端诊断数据由类型化 API Service 和轮询 Composable 管理。Springdoc 与 `openapi-typescript` 共同维护前后端契约，Vitest、Vue Testing Library 和 Playwright 覆盖组件及两条关键流程。选择这套小型栈是为了在桌面优先的四个运维视图中保持开发速度和契约一致性，不引入第二套组件库、实时通信或完整状态管理平台。
