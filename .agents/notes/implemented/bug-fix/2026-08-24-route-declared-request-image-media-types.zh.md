# Agent Note: 路由声明的请求图片媒体类型

Status: implemented

[English](2026-08-24-route-declared-request-image-media-types.md) | 中文

## Problem

llama.cpp 的 `llama-server` 暴露 OpenAI 兼容路由，其图像解码器接受 PNG 和 JPEG，但拒绝 WebP。harness 把带 alpha 的源图存为 WebP：带 alpha 的截图规范化为 WebP，pi-ai 的 OpenAI-completions 路由把它作为内联 base64 发送。这样的每个请求都确定性地失败为 HTTP 400 `Failed to load image or audio file`；由于会话历史持久保留规范化引用，该会话中每个后续轮次都会重新发送同一个已存储 WebP，并以同样方式失败。已存储附件按设计是提供方无关的；规范化无法知道每条路由的后端能解码什么，因此后端无法解码的已存储类型会破坏每个携带它的请求。

## Decision

`ImageRequestPolicy` 新增可选的 `mediaTypes` 字段，pi-ai 提供方 profile 以 `requestImageMediaTypes` 公开它。profile 把缺失或空列表解析为无声明；以空列表构造的策略以 `INVALID_ATTACHMENT_REF` 校验失败。无声明时流水线保留所有已存储媒体类型：处于预算内的已存储附件按字节原样直通，请求变体描述符省略该列表，既有请求图片缓存保持有效。

已声明的列表把直通限制为媒体类型被列表点名的已存储附件。其余已存储附件在请求时重编码为允许的媒体类型，使用过滤到允许媒体类型的既有色彩分支候选列表。当过滤后的候选列表为空——alpha 图片面对不含透明类型的列表，这是固定编码器集合唯一无法编码的类别——读取以 `UNSUPPORTED_IMAGE_TYPE` 失败，而不是降级图片。已声明列表是请求变体描述符的一部分，因此声明或取消列表会为相同的已存储字节派生新的变体；以不同列表写入的缓存条目永远不会满足读取。

内置 DeepSeek 路由解析无限制策略，因为其 Files API 接受 WebP。后端无法解码某个已存储类型的路由声明它能解码的类型；对 llama.cpp OpenAI 兼容服务器声明 `[image/png, image/jpeg]` 时，已存储的 WebP 截图以 PNG 发起请求。

## Alternatives considered

**在规范化（保存）时转换。** 按拥有该流水线的决策，规范化保持提供方无关；一个内容寻址对象服务每个读取它的路由，因此为能力最弱的后端选择最受限的已存储类型会劣化每条路由，而已发布的存储字节无法在不产生新对象的情况下改变。

**容忍提供方 400 并重试。** 失败是确定性的解码拒绝，而不是瞬时错误；重试相同字节永远不可能成功，浪费轮次，并且要求适配器特判一个响应正文不属于任何契约的提供方错误。

**按路由存储变体。** 为每个后端复制每个图片对象，迫使会话历史为同一已存储图片点名路由专属变体，并且既有机会会话中已存储的 WebP 依然存在。请求时重编码无需迁移即可恢复那些历史。

## Consequences

- 带 alpha 的截图面对 `[image/png, image/jpeg]` 路由时以带 alpha 的 PNG 发起请求，并在基于 stb 的后端解码；卡住的会话历史无需编辑日志即可恢复。
- 无限制路由（默认）保持按字节原样的直通和既有缓存身份；描述符仅在路由声明列表时不同。
- 受限候选列表保留所有图片类别可编码；唯一例外是 alpha 面对仅 JPEG 的列表，以 `UNSUPPORTED_IMAGE_TYPE` 失败。
- 需要在受限路由上保留 alpha 的部署必须在列表中包含透明类型。
- `request-image.spec.ts` 固定受限直通、受限重编码（alpha WebP 到 PNG、不透明 JPEG 到 PNG、低色数 PNG 到 JPEG）、alpha 拒绝、空列表校验和变体稳定性；`llm-pi-ai` 的 config 与 adapter 规格固定 profile 键的 schema、缺失与空列表解析和策略转发。

## Related

- [统一规范化附件、请求版本与提供方文件](../feature/2026-08-20-unified-image-request-pipeline.zh.md) 拥有本扩展所基于的流水线。
