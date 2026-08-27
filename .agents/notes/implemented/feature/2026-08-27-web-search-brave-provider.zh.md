# Agent Note: Brave Search 提供方

Status: implemented

[English](2026-08-27-web-search-brave-provider.md) | 中文

## 问题

web 能力家族将搜索后端注册到 `ctx.web`，而 `dsh-tool-web` 无论由哪个后端执行，都只暴露一个稳定的 `web_search` 工具。Exa、Perplexity 与 DeepSeek 提供方覆盖三个后端，但希望使用 Brave Search（另一个广泛可用的基于 API 的搜索服务）的部署没有可挂载的提供方。

新的搜索后端必须适配该 seam：不得改变面向模型的工具 schema，不得引入自己的错误词汇，并必须遵守 `dsh-web` 与 `dsh-tool-web` 拥有的提供方选择、取消与截断语义。

## 决策

`@deepseek-ai/dsh-web-search-brave` 向 `ctx.web` 注册 `brave` 搜索提供方。API key 来自插件的 `apiKey` 配置，缺失时在插件加载时回退到 `$BRAVE_API_KEY`；空 key 使提供方报告自身不可用。`baseURL` 默认为公共端点 `https://api.search.brave.com`；`count` 为未携带 `maxResults` 的请求提供默认结果数。

每次搜索是对 `{baseURL}/res/v1/web/search` 的 `GET`，携带 `q` 参数以及（若已定义）被钳制到 API 上限 20 的 `count` 参数。请求携带 `x-subscription-token`、`accept: application/json`、harness user agent、调用方的中止信号，并使用 `redirect: 'error'`。提供方从不跟随重定向：请求携带凭据，重定向会将其转发到任意目标；回归测试套件证明对重定向家族的每一个状态码，重定向目标都不会被访问。

`web.results[]` 映射为 `WebSearchSource` 条目：`url`、`title`、`description` 作为 snippet，以及 `page_age`（ISO-8601）作为 `publishedAt`；缺少 URL 或缺少非空 description 的条目被丢弃。提供方省略 `content` —— Brave 不返回生成的答案 —— 并始终报告 `truncated: false`，因为 `dsh-web` 在返回路径上强制执行请求的 `maxResults` 界限。

非 2xx 响应被解析为 Brave 的 `ErrorResponse` 信封并以 `WEB_PROVIDER_ERROR` 拒绝，消息为非空时的 `error.detail`，否则为 `Brave API error (HTTP <status>)`；无法解析的成功响应体同样是 `WEB_PROVIDER_ERROR`，消息为 `Brave returned an unprocessable response body: <error>`。调用方中止在任意时点（包括解析响应体中途）都呈现为 `WEB_ABORTED`。count 钳制只有上限：工具层已保证 `maxResults` 为正整数，因此直接经 seam 传入退化 count 的调用方会在 API 处诚实地失败，而不是被提供方悄悄纠正。

该提供方是独立的可选组件：作为独立包发布，在固定 `deepseek-official` 的已发布基础组合中没有配置行，沿用 Exa 与 Perplexity 的先例。

## 曾考虑的替代方案

**将 key 与设置服务及 Web Models 页面集成。** 被否决：该集成存在是为了让产品文档中的首次运行路径生效 —— DeepSeek 提供方通过 `ctx.credentials` 读取 `DEEPSEEK_API_KEY`，因为浏览器会存储它。Brave 不属于已发布的基础组合，也没有 UI 存储其 key，因此 Exa 与 Perplexity 确立的环境变量模式才是匹配的形态。

**跟随同源重定向（或 API 的默认重定向策略）。** 被否决：请求携带订阅 token，重定向会把凭据转发到任意目标。`redirect: 'error'` 将该缺陷转化为结构化失败，回归测试按状态码将其固定。

**请求 `extra_snippets` 以获得更丰富的结果文本。** 被否决：`description` 是规范 snippet 字段，额外 snippet 字段会消耗更多 API 配额，却不改变工具渲染的结果字段。

**同时将 `count` 向下钳制到 1。** 被否决：`dsh-tool-web` 在任何提供方看到 `maxResults` 之前已将其校验为正整数，因此直接经 seam 传入退化 count 的调用方应在 API 边界处失败，而不是由提供方悄悄纠正其输入。

**将该提供方挂载到已发布的基础组合中，或使其可从 Web Models 页面选择。** 被否决：基础组合固定 `deepseek-official`；Exa 与 Perplexity 是独立可选组件，Brave 沿用同一先例。希望使用 Brave 的部署在个人配置或 `--config` 叠加层中添加插件行，并设置 `dsh-web` 的 `searchProvider: brave`。

## 后果

一个部署只需一行插件配置和一个环境变量即可将 `web_search` 指向 Brave Search：没有新的工具 schema，没有新的错误码，除添加配置行并选择 id 外没有组合变更。结果以与其他后端相同的来源、标题、snippet 与发布日期到达模型，Brave 失败则呈现为工具错误包装下的提供方专属消息（`Brave search aborted`、`Brave search request failed: <error>`，或 API 的错误详情）。

作为交换，基于 Brave 的搜索返回来源而不返回生成的答案；携带凭据的请求上的重定向是硬错误而非传输行为；key 在插件加载时读取，因此轮转后的环境变量需要重启，与 Exa 和 Perplexity 一致。单元测试固定映射、可用性、请求构造、钳制、错误信封与中止；基于本地 `node:http` 服务器的重定向回归套件证明重定向目标不会被访问；可选的端到端测试在没有 `$BRAVE_API_KEY` 时自我跳过，在 key 存在时验证真实 API。

## 相关

- [Web 能力 seam——稳定的工具覆盖多个提供方](../architecture/2026-06-24-web-capability-seam.zh.md)
- [已交付组合中的默认 Web 搜索](2026-07-31-web-default-search.zh.md)
