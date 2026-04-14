# Torna Sync API - IntelliJ IDEA 插件

一键将 Spring MVC 接口的参数和返回值同步到 [Torna](https://torna.cn) 接口文档平台。

## 功能

- **右键方法** → Push to Torna：推送单个接口
- **右键类/文件** → Push to Torna：推送整个 Controller 的所有接口
- 自动解析 Spring MVC 注解（`@GetMapping`, `@PostMapping`, `@RequestMapping` 等）
- 自动提取请求参数（Query、Path、Header、RequestBody）和返回值结构
- 支持嵌套对象、泛型、集合类型的递归解析
- 识别 Swagger 注解（`@ApiOperation`, `@ApiModelProperty`, `@Operation`, `@Schema`）作为接口/字段描述
- 识别 Validation 注解（`@NotNull`, `@NotBlank`）标记必填字段
- 识别 Jackson 注解（`@JsonProperty`, `@JsonIgnore`）
- 推送前弹出预览对话框，确认后再执行

## 使用方法

### 1. 配置 Torna 连接

打开 `Settings → Tools → Torna Sync`，填写：

| 配置项 | 说明 |
|--------|------|
| 服务地址 | Torna 服务的地址，如 `http://localhost:7700` |
| Token | 在 Torna 项目的 OpenAPI 页面获取的推送 Token |
| 作者 | 文档作者名称（可选） |
| 默认文件夹 | 接口归属的文件夹，留空则使用 Controller 类名 |

### 2. 推送接口

- 在 Java Controller 文件中，**右键点击方法名** → 选择 `Push to Torna`，推送单个接口
- 在 Java Controller 文件中，**右键点击类名或空白处** → 选择 `Push to Torna`，推送该类的所有接口
- 在项目树中，**右键点击 Java 文件** → 选择 `Push to Torna`

### 3. 确认推送

弹出预览对话框，可以：
- 修改目标文件夹名称
- 查看将要推送的接口列表及参数详情
- 点击 OK 执行推送

## 支持的注解

### Spring MVC
- `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`
- `@RequestParam`, `@PathVariable`, `@RequestHeader`, `@RequestBody`

### Swagger / OpenAPI
- `@ApiOperation` / `@Operation` → 接口名称
- `@ApiModelProperty` / `@Schema` → 字段描述

### Validation
- `@NotNull`, `@NotBlank`, `@NotEmpty` → 标记为必填

### Jackson
- `@JsonProperty` → 自定义字段名
- `@JsonIgnore` → 忽略字段

## 构建

```bash
./gradlew buildPlugin
```

构建产物在 `build/distributions/` 目录下。

## 要求

- IntelliJ IDEA 2025.3+
- Java 21+
