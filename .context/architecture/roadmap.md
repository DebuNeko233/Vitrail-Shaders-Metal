# Vitrail × Metallum Metal Shader Backend 迁移路线图

> Status: Architecture / Migration Plan  
> Target: Minecraft 26.2  
> Graphics backend: Apple Metal  
> Runtime target: Vitrail Shader Pack Runtime + Neo Metallum Metal Backend  
> Primary platform: macOS / Apple Silicon  
> Principle: **正确性优先，语义优先，逐层验证，失败可回退**

---

# 1. 项目目标

本项目的最终目标不是在 Neo Metallum 内重新实现一套完整的 Iris / OptiFine Shader Pack Runtime，而是建立如下职责清晰的架构：

```text
Shader Pack
    │
    ▼
Vitrail
    │
    │ 负责 Shader Pack 语义
    │
    ├─ Pack parsing
    ├─ Program routing
    ├─ GLSL translation
    ├─ Uniform / sampler semantics
    ├─ GBuffer semantics
    ├─ colortex/depthtex/shadowtex
    ├─ Shadow
    ├─ Deferred
    ├─ Composite
    ├─ Final
    ├─ Dimension routing
    ├─ Shader options
    └─ Shader Pack compatibility
    │
    ▼
Minecraft 26.2 Graphics API
    │
    ├─ GpuDevice
    ├─ CommandEncoder
    ├─ RenderPass
    ├─ RenderPipeline
    ├─ GpuTexture
    ├─ GpuBuffer
    └─ GpuSampler
    │
    ▼
Neo Metallum
    │
    │ 负责 Apple Metal 执行
    │
    ├─ MetalDevice
    ├─ MetalCommandEncoder
    ├─ MetalRenderPass
    ├─ MetalPipeline
    ├─ MetalTexture
    ├─ MetalBuffer
    ├─ MetalSampler
    ├─ SPIR-V → MSL
    ├─ Synchronization
    └─ CAMetalLayer / Presentation
    │
    ▼
Apple Metal
    │
    ▼
Apple Silicon GPU
```

核心职责定义：

```text
Vitrail 回答：
“Shader Pack 想表达什么？”

Metallum 回答：
“这些 GPU 操作如何在 Apple Metal 上执行？”
```

必须长期维持这一边界。

---

# 2. 项目基本原则

## 2.1 语义优先，而不是“能画出来”

任何功能完成的标准都不是：

```text
不崩溃
```

也不是：

```text
屏幕上有图像
```

而必须是：

```text
Shader Pack 所要求的语义与实际 GPU 行为一致。
```

因为图形错误中大量问题会产生“看起来合理但实际上错误”的图像，例如：

- ping-pong target 读错半边；
- MRT attachment 顺序错误；
- depthtex 时机错误；
- reversed-Z 未转换；
- alpha cutout 丢失；
- blending 错误；
- dimension fallback 错误；
- shadow depth convention 错误；
- 上一帧内容被误读；
- sampler slot 错位；
- vertex attribute stride 错误。

因此：

> **Plausible image ≠ Correct image**

---

# 3. 架构约束

## 3.1 禁止在 Vitrail 中重写完整 Metal Renderer

不采用：

```text
MetalPackChain
MetalColorTargets
MetalTerrainDraw
MetalShadowRenderer
MetalCompositeRenderer
```

Vitrail 的绝大部分 Shader Pack Runtime 应保持 backend-neutral。

优先依赖 Minecraft 26.2 提供的：

```java
GpuDevice
CommandEncoder
RenderPass
RenderPipeline
GpuTexture
GpuTextureView
GpuBuffer
GpuSampler
```

只有 Minecraft API 无法表达的能力，才允许增加 backend extension。

---

# 4. Backend Extension 原则

Vitrail 不应该直接操作 Metal 原生对象，也不应该假定某个具体图形 API。

采用小型 capability interface，而不是建立一个巨大的：

```text
RenderBackend
```

建议能力拆分：

```text
BackendCapabilities
├─ MipmapCommands
├─ PipelineCacheAccess
├─ FormatCapabilities
├─ StorageBindingAccess
├─ ComputeAccess
├─ PassControl
├─ ShaderModuleAccess
└─ SynchronizationCapabilities
```

例如：

```java
public interface MipmapCommands {
    boolean generateMipmaps(GpuTexture texture);
}
```

而不是：

```java
backend.metalPipelineBarrier(...);
```

Backend abstraction 必须表达：

```text
GPU 语义
```

而不是表达：

```text
具体图形 API
```

错误示例：

```text
MTLRenderCommandEncoder
MTLBarrierScope
MTLTextureUsage
```

这些不能出现在 backend-neutral API 中。

正确接口应描述：

```text
前一阶段写入完成
下一个阶段需要读取
生成 mipmaps
结束当前 encoder
使 attachment 可被采样
```

---

# 5. 推荐模块关系

最终推荐：

```text
vitrail
│
├─ shader pack runtime
├─ translator
├─ render graph
├─ pack parser
├─ backend extension API
└─ Minecraft integration
        │
        ▼
  vitrail-metallum
        │
        ▼
   neo-metallum
```

依赖方向：

```text
Vitrail
   X
   │
   │ 不依赖
   ▼
Metallum
```

而是：

```text
vitrail-metallum
   ├─ depends on Vitrail
   └─ depends on Metallum
```

这样：

- Vitrail 不被 Metal 原生细节污染；
- Metallum 不被 Shader Pack Runtime 污染；
- bridge 可以独立迭代；
- Metal 原生对象只在 backend 一侧出现；
- 更容易定位回归；
- 更容易维护许可证边界。

---

# 6. Shader 编译架构

长期目标：

```text
OptiFine / Iris GLSL
        │
        ▼
Vitrail ProgramTranslator
        │
        ├─ legacy built-ins
        ├─ uniforms
        ├─ samplers
        ├─ varyings
        ├─ vertex attributes
        ├─ alpha test
        ├─ coverage
        ├─ draw buffers
        └─ Shader Pack semantics
        │
        ▼
Translated GLSL
        │
        ▼
Minecraft / Vitrail shader compilation
        │
        ▼
SPIR-V
        │
        ├─ Vitrail SPIR-V patch
        ├─ reflection
        └─ resource mapping
        │
        ▼
Metallum
        │
        ▼
SPIRV-Cross
        │
        ▼
MSL
        │
        ▼
Metal Shader Function
```

因此 Neo Metallum 不应长期继续维护：

```text
LegacyTerrainTranslator
LegacyEntityTranslator
LegacySodiumTerrainTranslator
```

这些属于 Shader Pack Runtime，而不是 Metal backend。

---

# 7. 第一阶段重构原则

第一阶段不是：

```text
开始写 Metal Vitrail
```

第一阶段必须是：

```text
让现有 Vitrail 实现经过 backend abstraction 后，
对 Shader Pack 可见的行为完全不变。
```

流程：

```text
Vitrail 现有实现
        │
        ▼
抽出 Backend Extension
        │
        ▼
由 backend 实现 Backend Extension
        │
        ▼
验证 Shader Pack 语义零变化
```

抽出的目的不是让多个 GPU backend 并行存在，而是把 Metal 原生细节隔离在 seam 的
一侧。产品支持面只有一个 provider：Metallum / Metal，因此不存在第二个 backend
的回归 baseline；"不变" 指的是 pack 观察到的东西不变，而不是某个 backend 被保留
下来。没有任何路径可以回退到别的图形 API。

---

# 8. Backend 具体化已完成的清除面

这一节记录的是已经完成的工作。最初需要优先处理的是：

```text
CommandEncoder / Device / RenderPass 上的 backend mixin
GlslCompilerMixin
BindGroupLayout 访问
Sodium DrawContext 注入点
ComputeShader
PushedDescriptor
StalePipelines
Texture / format capability queries
```

这些具体实现已不再保留。目标是：

```text
具体图形 API implementation
            │
            ▼
backend-neutral interface
            ▲
            │
 Metal implementation
```

边界的方向是单向的：seam 只描述 GPU 语义，Metal 原生对象、descriptor index、
resource usage 与 encoder 对象都留在 backend 一侧，不存在第二个 implementation，
也不存在为它保留的行为分支。

---

# 9. Metallum 当前最重要的基础能力：MRT

Vitrail Shader Pack Runtime 的核心基础不是 Shadow，也不是 Composite。

而是：

# Multi Render Target

Shader Pack 必须支持：

```text
colortex0
colortex1
colortex2
colortex3
...
```

一个 draw 同时写入多个 Render Target。

当前单 attachment 模型：

```text
RenderPass
    │
    ▼
colorAttachments.getFirst()
    │
    ▼
Metal colorAttachment[0]
```

必须升级为：

```text
RenderPass
    │
    ▼
colorAttachments[0..N]
    │
    ▼
MTLRenderPassDescriptor
    │
    ├─ colorAttachments[0]
    ├─ colorAttachments[1]
    ├─ colorAttachments[2]
    └─ ...
```

---

# 10. Metal Pipeline 必须同步支持 MRT

Pipeline key 不能只包含 shader。

至少应包含：

```text
PipelineKey
├─ vertex shader
├─ fragment shader
├─ vertex format
├─ topology
├─ depth format
├─ stencil format
├─ sample count
├─ attachment count
├─ color format[0]
├─ color format[1]
├─ ...
├─ blend state[0]
├─ blend state[1]
└─ ...
```

否则：

```text
同一个 shader
+
不同 framebuffer
```

可能错误复用 Metal pipeline。

---

# 11. Per-Attachment Blend

必须允许：

```text
Attachment 0
    blend = alpha

Attachment 1
    blend = disabled

Attachment 2
    blend = additive
```

Metal 原生支持：

```text
MTLRenderPipelineColorAttachmentDescriptor[0]
MTLRenderPipelineColorAttachmentDescriptor[1]
...
```

因此 Metallum 应从一开始设计成 attachment-local state。

不要建立全局：

```text
oneBlendStateForAllAttachments
```

---

# 12. Synchronization 方法论

严禁把底层 barrier API 逐个照搬为另一个 API 的 barrier。

Metal：

```text
encoder lifetime
command buffer ordering
MTLFence
render → blit
blit → render
render → compute
compute → render
```

应该抽象的是：

```text
Dependency
```

而不是：

```text
Barrier API
```

例如：

```text
Pass A writes texture
        │
        ▼
end Render Encoder
        │
        ▼
Fence / Metal ordering
        │
        ▼
Pass B samples texture
```

---

# 13. Encoder 生命周期必须严格

任何时刻 GPU encoder 状态必须明确：

```text
NONE
RENDER
BLIT
COMPUTE
```

不得出现：

```text
Render Encoder 尚未结束
→ 开 Blit Encoder
```

所有 backend command 必须保证：

```text
begin
→ encode
→ end
```

生命周期完整。

异常路径同样必须关闭 encoder。

---

# 14. Mipmap 策略

Vitrail runtime 只声明：

```text
此 texture 需要 mipmap
```

具体实现交给 backend。

Metal 优先：

```text
MTLBlitCommandEncoder.generateMipmapsForTexture
```

必要时才采用手工 level-to-level blit。

Vitrail 不得知道 Metal mipmap 实现方式。

---

# 15. Sodium 接入策略

原先注入的是 backend-specific 的 draw context：

```text
Sodium DrawContext#setContext
```

不能成为长期 Vitrail API。

应该抽象成事件：

```text
Terrain RenderPass opened
        +
Terrain pipeline selected
        │
        ▼
TerrainDraw.bind(...)
```

不同 backend 曾各有一条注入路径，现在只剩一条：

```text
Sodium + Metallum
      │
      └──► TerrainBindHook
```

业务逻辑只存在一份。

---

# 16. Geometry / Entity / Hand 统一原则

禁止继续为每类对象创建独立 Shader Runtime。

应该统一：

```text
GeometryProgram
    │
    ├─ TERRAIN
    ├─ ENTITY
    ├─ BLOCK_ENTITY
    ├─ HAND
    ├─ CLOUD
    ├─ WEATHER
    ├─ PARTICLE
    └─ SKY
```

区别由：

```text
Program family
Vertex format
Uniform data
Sampler data
Draw origin
Material / entity ID
```

决定。

不是重新复制整条 pipeline。

---

# 17. Vertex Format 是严格 ABI

必须把 vertex layout 当作 ABI。

任何 Shader attribute：

```text
location
offset
format
stride
buffer slot
```

必须与 Metal descriptor 一致。

禁止：

```text
Shader 需要 attribute
但 vertex descriptor 没有
```

也禁止：

```text
descriptor 声明 attribute
但 stride 对不上
```

每一种扩展 vertex format 都应有自动测试。

---

# 18. Alpha Test / Cutout 是 Render Pass 语义

例如：

```text
solid
cutout
translucent
```

不应该只依赖 Shader Program 名字。

同一个：

```text
gbuffers_terrain
```

可能服务多个 terrain pass。

因此应携带：

```text
PassSemantic
├─ alpha test
├─ blend
├─ depth write
├─ depth compare
└─ coverage
```

避免再次出现：

```text
fire
grass
leaves
grass_block_side_overlay
```

透明像素没有 discard 的问题。

---

# 19. Render Target 系统原则

Vitrail 应作为以下语义的唯一来源：

```text
colortex
depthtex
shadowtex
ping-pong
target format
target size
clear color
mipmap
flip
history
```

Metallum 只实现：

```text
Texture
TextureView
Attachment
Clear
Sample
Blit
Render
```

不要在 Metallum 内复制：

```text
Shader Pack Target Scheduler
```

---

# 20. Ping-Pong 不变量

Shader Pack framebuffer 有一个极其重要的不变量：

> 每一次读取，都必须读取在该次读取之前最后一次写入所产生的 surface。

禁止通过：

```text
当前 frame parity
```

简单猜。

必须由 Vitrail 的：

```text
TargetPlan
TargetSchedule
```

决定。

Metallum 不计算 Shader Pack flip。

---

# 21. Depth 体系

必须明确区分：

```text
scene depth
depthtex0
depthtex1
depthtex2
shadow depth
hand depth
pre-translucent depth
```

不能简单 alias 成同一张 texture。

尤其必须明确：

```text
Minecraft reversed-Z
≠
Legacy Shader Pack depth convention
```

转换必须只发生在明确的边界。

---

# 22. Scene Seed 原则

在部分 geometry 尚未通过 Shader Pack program 绘制时，可以继续采用：

```text
Minecraft native rendered scene
        │
        ▼
SceneSeed
        │
        ▼
Shader Pack colortex
```

但 SceneSeed 只能作为：

```text
兼容性过渡路径
```

不能视为最终正确实现。

因为它已经包含：

```text
Minecraft lighting
fog
tone mapping
gamma
```

无法恢复：

```text
normal
material
raw albedo
```

因此每增加一个真实 Shader Pack geometry family，就应该减少 seed 覆盖范围。

---

# 23. Shadow 实现顺序

Shadow 必须在：

```text
MRT
Target system
Depth
Pipeline layout
```

全部稳定以后再实现。

流程：

```text
Shadow camera
   │
   ▼
shadow geometry
   │
   ▼
shadowtex0
shadowtex1
shadowcolor*
   │
   ▼
mipmap
   │
   ▼
deferred/composite sampling
```

Shadow 不应该先于 MRT。

---

# 24. Deferred / Composite / Final

正确顺序：

```text
Geometry
   │
   ▼
GBuffer
   │
   ▼
Deferred
   │
   ▼
Translucent Geometry
   │
   ▼
Composite
   │
   ▼
Final
   │
   ▼
Minecraft main target
   │
   ▼
Metal presentation
```

不要直接从：

```text
gbuffers_terrain
```

跳到：

```text
final
```

然后用视觉结果判断兼容性。

---

# 25. Compute / Storage 的策略

Compute 属于后期能力。

第一阶段允许：

```text
compute unsupported
storage image unsupported
SSBO unsupported
```

但必须：

```text
明确检测
明确日志
明确禁用对应 program
```

禁止：

```text
静默忽略
```

因为静默忽略会产生：

```text
“可以运行但图像错误”
```

这是最危险的状态。

---

# 26. Sampler / Resource Binding

Metal resource slots 必须严格规划。

不能假定：

```text
GLSL binding == Metal index
```

应该：

```text
Shader reflection
    │
    ▼
Logical resource layout
    │
    ▼
Metal resource mapping
```

统一管理：

```text
textures
samplers
uniform buffers
storage buffers
storage images
argument buffers
```

高 sampler 数量 Shader Pack 后续可使用：

```text
Metal Argument Buffers
```

而不是强行占用固定 fragment texture slots。

---

# 27. Dimension Routing

Dimension routing 属于 Vitrail Shader Pack Runtime。

不是 Metallum 功能。

应该支持：

```text
root shaders
world0
world-1
world1
dimension.properties
custom dimension mapping
```

必须遵守 Shader Pack 的真实 fallback 语义。

禁止简单：

```text
dimension program missing
→ 永远 fallback root
```

因为有些 pack 通过缺失 program 有意禁用某一维度效果。

---

# 28. Shader Settings

Shader settings 同样属于 Vitrail。

包括：

```text
#define options
profiles
screens
sub-pages
sliders
translations
shaders.properties
```

Metallum 不解析这些。

最终：

```text
User setting
   │
   ▼
Vitrail
   │
   ▼
Translated ShaderSource
   │
   ▼
Metallum compilation
```

---

# 29. Fail-Safe 原则

任何 Shader Pack 错误都不能导致：

```text
Minecraft native Metal renderer 崩溃
```

错误处理层级：

```text
Shader Program failure
        │
        ▼
Disable affected shader program
        │
        ▼
Fallback family
        │
        ▼
Fallback native Minecraft pipeline
```

而不是：

```text
throw
→ crash game
```

但：

```text
Metal backend 自身状态损坏
```

属于 backend fatal error，可以终止。

---

# 30. 日志规范

日志必须回答：

```text
发生了什么
发生在哪个 pack
哪个 program
哪个 stage
哪个 backend
哪个 attachment
为什么 fallback
fallback 到哪里
```

推荐格式：

```text
[Vitrail/Metal]
Pack=Photon
Program=gbuffers_terrain
Stage=fragment
Backend=Metal
Result=Fallback
Reason=Unsupported storage image
Fallback=Native terrain pipeline
```

严禁：

```text
shader failed
```

这种不可定位日志。

---

# 31. Debug 模式

建议提供：

```text
-Dvitrail.debug=true
-Dmetallum.debug=true
```

额外输出：

```text
translated GLSL
SPIR-V
MSL
pipeline layout
vertex descriptor
MRT attachments
sampler bindings
uniform bindings
render pass order
target flips
target formats
depth copies
```

Release 默认关闭详细 dump。

---

# 32. Testing Pyramid

必须建立四层测试。

## Level 1：纯 Java 测试

用于：

```text
pack parser
dimension routing
option parsing
target scheduling
program fallback
flip parity
shader transformation
```

不依赖 GPU。

---

## Level 2：Shader 编译测试

验证：

```text
GLSL
→ SPIR-V
→ MSL
```

覆盖：

```text
terrain
entity
hand
MRT
alpha cutout
samplers
uniforms
```

---

## Level 3：Metal Contract Smoke Tests

创建极小 shader。

例如 MRT：

```text
RT0 = red
RT1 = green
RT2 = blue
RT3 = white
```

读取结果检查 attachment。

类似 smoke test 应包括：

```text
MRT
depth
alpha cutout
blend
sampler
mipmap
ping-pong
vertex ABI
```

---

## Level 4：真实 Minecraft 测试

至少固定：

```text
Apple Silicon
Minecraft 26.2
Fabric
Sodium
Neo Metallum
Vitrail
```

测试场景必须可复现。

---

# 33. Smoke Pack 体系

不要一开始使用 Photon 定位基础问题。

建立逐层 smoke packs：

```text
00-basic-color
01-terrain
02-cutout
03-entity
04-block-entity
05-hand
06-mrt
07-depth
08-shadow
09-deferred
10-composite
11-final
12-dimension
13-options
14-history
15-compute
```

每个 pack 只测试一种能力。

这样出现问题时可以立即知道：

```text
Runtime
Backend
Shader translation
Resource binding
Render target
```

是哪一层。

---

# 34. Real Pack 验证阶梯

基础 smoke test 通过后：

```text
Stage A
简单 legacy shader

Stage B
Sildur / MakeUp 类中等复杂度

Stage C
Complementary

Stage D
Photon

Stage E
复杂 Compute / PBR / temporal packs
```

Photon 不应该成为最初开发测试包。

---

# 35. CI 原则

Linux CI 负责：

```text
Java compilation
Mixin closure
API compatibility
unit tests
shader preprocessing tests
SPIR-V compilation tests
```

Linux CI 不能证明：

```text
Metal ABI 正确
Metal pipeline 正确
Metal synchronization 正确
Apple GPU 行为正确
```

因此：

```text
Linux CI PASS
≠
Metal feature validated
```

必须明确标记：

```text
CI Verified
Real-device Verified
```

两种状态。

---

# 36. PR 开发纪律

每个 PR 只完成一个清晰里程碑。

禁止：

```text
PR #2 未关闭
→ 开始 #3 大量正式开发
```

允许：

```text
实验分支
```

但必须明确：

```text
EXPERIMENTAL
DO NOT MERGE
```

正式流程：

```text
Milestone
   │
   ▼
Implementation
   │
   ▼
CI
   │
   ▼
Smoke Test
   │
   ▼
Real Device
   │
   ▼
Docs Sync
   │
   ▼
PR Ready
   │
   ▼
Merge
   │
   ▼
Next Milestone
```

---

# 37. Definition of Done

每个阶段只有满足以下条件才能 DONE：

```text
[ ] Implementation complete
[ ] Unit tests complete
[ ] CI green
[ ] No known startup regression
[ ] Native fallback works
[ ] Debug logs sufficient
[ ] Smoke test passes
[ ] Real Apple Silicon test passes
[ ] Documentation updated
[ ] AGENTS.md updated
[ ] Known limitations documented
[ ] No unrelated feature bundled
```

---

# 38. 禁止条件

出现以下情况不得 merge：

```text
启动崩溃
世界加载崩溃
native Metal fallback 失效
无 Shader Pack 时画面变化
严重 GPU validation error
资源生命周期错误
pipeline cache 跨错误状态复用
MRT attachment 错位
vertex ABI mismatch
明显同步错误
```

---

# 39. Roadmap 总览

```text
PHASE 0
Architecture Freeze
        │
        ▼
PHASE 1
Vitrail Backend Neutralization
        │
        ▼
PHASE 2
Metallum MRT Foundation
        │
        ▼
PHASE 3
Vitrail ↔ Metallum Bridge
        │
        ▼
PHASE 4
Fullscreen Pack Pass
        │
        ▼
PHASE 5
Terrain / Sodium Integration
        │
        ▼
PHASE 6
GBuffer MRT
        │
        ▼
PHASE 7
Entities / Block Entities / Hand
        │
        ▼
PHASE 8
Depth System
        │
        ▼
PHASE 9
Shadow
        │
        ▼
PHASE 10
Deferred
        │
        ▼
PHASE 11
Composite
        │
        ▼
PHASE 12
Final
        │
        ▼
PHASE 13
Dimension Routing
        │
        ▼
PHASE 14
Advanced Samplers / Argument Buffers
        │
        ▼
PHASE 15
Compute / Storage
        │
        ▼
PHASE 16
Advanced PBR / History / Temporal
        │
        ▼
PHASE 17
Real Pack Compatibility
```

---

# 40. PHASE 0 — Architecture Freeze

目标：

```text
在继续功能开发之前固定职责边界。
```

完成：

```text
[ ] 本文档进入仓库
[ ] AGENTS.md 引用本文档
[ ] Shader Runtime 与 Metal Backend 职责明确
[ ] 停止向 Metallum 添加新的 Shader Pack 高层语义
```

---

# 41. PHASE 1 — Vitrail Backend Neutralization

目标：

```text
Vitrail 行为不变，
但 runtime 不再依赖任何具体图形 API 的类型。
```

重点：

```text
MipmapCommands
PipelineCacheAccess
FormatCapabilities
ShaderModuleAccess
TerrainBindHook
StorageBinding abstraction
```

验收：

```text
Vitrail
行为与重构前一致
```

---

# 42. PHASE 2 — Metallum MRT Foundation

实现：

```text
N color attachments
per-attachment pixel format
per-attachment blend
pipeline MRT key
MRT validation
MRT clear
MRT store
```

Smoke test：

```text
RT0 red
RT1 green
RT2 blue
RT3 white
```

这是后续阶段的硬依赖。

---

# 43. PHASE 3 — Vitrail Metallum Bridge

实现：

```text
backend discovery
Metal capability provider
Metal mipmap
pipeline invalidation
terrain bind bridge
format query
```

目标：

```text
Vitrail runtime 可以识别 Metallum backend。
```

此时仍不要求完整 Shader Pack。

---

# 44. PHASE 4 — Fullscreen Pack Pass

第一个真正执行的 Vitrail shader：

```text
fullscreen vertex
+
fragment
```

目标：

```text
Shader Pack GLSL
→ Vitrail
→ SPIR-V
→ Metallum
→ MSL
→ Metal
```

仅验证编译和资源绑定。

---

# 45. PHASE 5 — Terrain

接入：

```text
Sodium terrain
gbuffers_terrain
```

必须验证：

```text
solid
cutout
translucent
alpha test
grass overlay
fire
flowers
leaves
water
```

这一步必须彻底解决 pass semantics。

---

# 46. PHASE 6 — GBuffer MRT

开始真正输出：

```text
colortex0
colortex1
colortex2
...
```

验证：

```text
attachment location
format
clear
write
sampling
ping-pong
```

---

# 47. PHASE 7 — Entity Families

依次：

```text
entities
block entities
spider eyes
armor glint
hand
hand_water
particles
weather
clouds
sky
```

每一种必须验证 vertex ABI。

---

# 48. PHASE 8 — Depth System

实现：

```text
depthtex0
depthtex1
depthtex2
pre-translucent
pre-hand
depth conversion
```

必须有专门 depth smoke test。

---

# 49. PHASE 9 — Shadow

实现：

```text
shadow terrain
shadow entities
shadow depth
shadow color
shadow mipmaps
```

先 shadow 基础，再高级 shadow composite。

---

# 50. PHASE 10 — Deferred

实现：

```text
deferred
deferred1
deferred2
...
```

正确处理：

```text
read/write surface
target flip
depth
MRT
mipmap
```

---

# 51. PHASE 11 — Composite

实现：

```text
composite
composite1
...
```

验证：

```text
temporal target
previous-frame content
flip parity
history preservation
```

---

# 52. PHASE 12 — Final

最终：

```text
Shader Pack final
        │
        ▼
Minecraft main target
        │
        ▼
Metallum
        │
        ▼
CAMetalLayer
```

到这一阶段才算形成完整 Shader Pack frame。

---

# 53. PHASE 13 — Dimension Routing

实现：

```text
world0
world-1
world1
dimension.properties
custom dimensions
```

必须建立自动测试验证 fallback。

---

# 54. PHASE 14 — Wide Resources

解决大型 Shader Pack：

```text
> 16 samplers
large resource sets
```

Metal 优先：

```text
Argument Buffers
```

不要为兼容 pack 人为限制资源数量。

---

# 55. PHASE 15 — Compute / Storage

最后再增加：

```text
compute shaders
SSBO
storage images
imageLoad
imageStore
```

需要：

```text
MTLComputePipelineState
MTLComputeCommandEncoder
```

和 Render/Blit encoder 建立严格同步。

---

# 56. PHASE 16 — Advanced Features

包括：

```text
PBR
normal/specular maps
custom textures
3D textures
voxel volumes
temporal history
motion vectors
advanced shadow
custom noise
advanced blending
```

---

# 57. PHASE 17 — Real Shader Pack Compatibility

最终验证：

```text
Photon
Complementary
BSL-family
Sildur-family
MakeUp
其他大型 OptiFine/Iris packs
```

兼容性报告必须区分：

```text
Supported
Partially Supported
Fallback
Unsupported
Broken
```

不得只写：

```text
Compatible
```

---

# 58. 现有 Neo Metallum Shader Pack Runtime 的迁移策略

当前已有工作不是全部删除。

第一阶段：

```text
保留
```

用于：

```text
Metal backend 验证
回归测试
fallback
```

第二阶段：

```text
Vitrail 对应能力成熟
        │
        ▼
逐项标记 deprecated
```

第三阶段：

```text
删除重复 runtime
```

迁移表：

```text
ShaderPackManager
    → Vitrail PackChoice / runtime

LegacyTerrainTranslator
    → Vitrail ProgramTranslator

LegacyEntityTranslator
    → Vitrail GeometryProgram

Shader Pack UI
    → Vitrail settings UI

dimension routing
    → Vitrail

future MRT scheduling
    → Vitrail TargetPlan / TargetSchedule
```

Metallum 最终不保留 Shader Pack policy。

---

# 59. Robustness Checklist

每次修改 rendering core 前必须回答：

```text
1. 这个逻辑属于 Shader Runtime 还是 Metal Backend？

2. 是否破坏无 Shader Pack 场景？

3. 是否有 native fallback？

4. 是否改变 pipeline cache identity？

5. 是否改变 vertex ABI？

6. 是否改变 RenderPass attachment？

7. 是否改变资源生命周期？

8. 是否影响 resize？

9. 是否影响 resource reload？

10. 是否影响 dimension change？

11. 是否影响 shader reload？

12. 是否改变 synchronization？

13. 是否需要更新 smoke test？

14. 是否需要更新文档？

15. 是否已经在 Apple Silicon 实机验证？
```

---

# 60. 最终工程目标

最终项目应达到：

```text
Minecraft 26.2
        │
        ▼
Vitrail Shader Runtime
        │
        ▼
Minecraft Graphics Abstraction
        │
        ▼
Neo Metallum
        │
        ▼
Native Apple Metal
```

并满足：

```text
Shader Runtime 与 GPU Backend 完全解耦

无 Shader Pack 时：
Metallum 行为与原生一致

Shader Pack 出错时：
能够安全回退

资源生命周期：
明确且可验证

RenderPass 顺序：
确定性

Target scheduling：
只有一个权威实现

Vertex ABI：
严格一致

Pipeline cache：
不会跨错误状态复用

CI：
验证代码和 API

Apple Silicon：
验证真实 GPU 行为

每个阶段：
有 smoke test

每个 PR：
有明确 Definition of Done
```

---

# 61. 项目长期原则

整个项目后续开发始终遵守以下优先级：

```text
Correctness
    >
Robustness
    >
Compatibility
    >
Maintainability
    >
Performance
    >
Feature Count
```

性能优化不得破坏正确性。

兼容性补丁不得成为：

```text
Photon-specific hack
Complementary-specific hack
某个 Shader Pack 的硬编码
```

任何真实 Shader Pack 暴露的问题，都应该追溯到：

```text
缺失的 Shader Pack contract
缺失的 Minecraft contract
缺失的 Metal capability
错误的 Runtime semantic
```

然后修复该层。

---

# 62. 一句话项目准则

> **Vitrail 定义 Shader Pack 应该发生什么，Metallum 保证这些操作在 Apple Metal 上正确发生；任何跨越这条边界的逻辑都必须重新审视。**