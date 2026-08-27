# @deepseek-ai/dsh-web-search-brave

[English](README.md) | 中文

由 [Brave Search](https://search.brave.com) 支持的 `WebSearchProvider`，用于 harness [web 能力 seam](../web/README.zh.md)（`ctx.web`）。它调用 Brave 的 `GET /res/v1/web/search` 端点并携带 `x-subscription-token` 请求头，把 `web.results[]` 映射为 seam 规范化的 `WebSearchResult`。

这是一个**实现**包：它向 `ctx.web` 注册提供方，不拥有 `ctx.web` 键，也不注册面向模型的工具（后者属于 `@deepseek-ai/dsh-tool-web`）。与 `@deepseek-ai/dsh-llm-deepseek` 一样，它是函数／命名空间插件（`inject: ['web']`），负责注册后端，而非默认导出服务。

## 配置

| 配置键 | 默认值 | 含义 |
|---|---|---|
| `apiKey` | `$BRAVE_API_KEY` | Brave API 密钥。为空或缺失时提供方不可用。 |
| `baseURL` | `https://api.search.brave.com` | 端点基址；追加 `/res/v1/web/search`。无法解析时提供方不可用。 |
| `count` | （未设置） | 请求不含 `maxResults` 时使用的默认结果数。未设置时不发送默认值。必须是正整数。 |

```yaml
- id: web-search-brave
  name: '@deepseek-ai/dsh-web-search-brave'
  config:
    apiKey: !!js process.env.BRAVE_API_KEY
```

## 映射

Brave 返回 `web.results[]`，不返回生成答案，因此省略 `content`。每项结果映射为 `WebSearchSource`：`url` ← `url`、`title` ← `title`、`snippet` ← `description`（没有 description 的结果缺少可移植的 snippet，会被丢弃）、`publishedAt` ← `page_age`。请求的 `maxResults` 优先于已配置的默认 `count`；发送的 `count` 会被钳制到 Brave 文档记载的最大值 20，最终上限由 seam 强制执行。提供方失败（HTTP 错误、网络失败、响应体无法解析或结构不符）以 `WebError` `WEB_PROVIDER_ERROR` 呈现，存在错误信封 `detail` 文本时采用之；中止请求以 `WEB_ABORTED` 呈现。HTTP 重定向会在访问 `Location` 指向的目标之前被拒绝，并以 `WEB_PROVIDER_ERROR` 呈现。

## 模型体验

通过 [`dsh-tool-web`](../tool-web/README.zh.md) 间接影响；该工具保留此提供方经 `maxResults` 限制的 URL、标题、description 摘要与发布日期，或将确切的错误消息 `Brave search aborted`、`Brave search request failed: <error>` 和 `Brave returned an unprocessable response body: <error>` 置于消费方的错误包装层内；提供方私有字段不进入上下文。

#### KV Cache 影响

不会直接导致 KV Cache 失效；请求前缀变更由上述消费方负责。

## 已知限制与暂缓事项

- **没有非空白 `description` 的结果会被整个丢弃**：没有可映射的可移植 snippet，因此返回源可能少于请求数量。
- **发送的 `count` 会被钳制到 Brave 文档记载的最大值 20**：请求的 `maxResults` 超过 20 时最多取回 20 个源；返回路径上 seam 仍会强制执行完整上限。
- **只公开 `count`**：Brave 的其他控制项（search type、新鲜度、地理位置、额外摘要、全文内容）等待提供方无关的 Service Definition 字段（见 [seam Agent Note](../../../.agents/notes/implemented/architecture/2026-06-24-web-capability-seam.zh.md)）。
- **按错误形状分类中止**：只有 `DOMException` 且名为 `AbortError` 时才映射为 `WEB_ABORTED`；携带自定义原因的中止（例如 `dsh-timeout` 的 `TimeoutReason`）会呈现为 `WEB_PROVIDER_ERROR`。
