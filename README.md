# Quick Code — AI 代码生成平台

Quick Code 是一个基于 **AI 对话** 的 Web 应用生成平台。用户用自然语言描述需求，系统会自动选择生成模式、编排工作流、生成可运行代码，并支持实时预览、一键部署与增量修改。

项目采用 **前后端分离** 架构：Spring Boot 后端负责 AI 编排与代码生成，Vue 3 前端提供对话式交互与可视化预览。

---

## 核心功能

### 用户功能

| 功能 | 说明 |
|------|------|
| 应用创建 | 输入一句话描述，AI 自动判断生成类型并创建应用 |
| AI 对话生成 | 通过 SSE 流式对话生成网站，实时展示生成进度与预览 |
| 实时预览 | 右侧 iframe 实时预览 HTML / 多文件 / Vue 工程效果 |
| 可视化编辑 | 在预览区点选元素，将修改意图发送给 AI 继续迭代 |
| 增量修改 | 基于已有代码继续对话修改，无需每次从零生成 |
| 一键部署 | 生成完成后部署到云端，获得可访问 URL |
| 代码下载 | 下载完整项目源码（Vue 工程打包为 zip） |
| 应用管理 | 查看、重命名、删除自己的应用 |
| 精选应用 | 浏览平台精选的优秀案例 |

### 管理员功能

- 应用管理：查询、编辑、删除任意应用，设置精选优先级
- 用户管理：用户信息维护
- 对话管理：查看全站对话历史

---

## 三种代码生成模式

系统会根据用户需求复杂度，由 AI **自动路由** 到合适的生成模式：

| 模式 | 标识 | 适用场景 | 输出 |
|------|------|----------|------|
| 原生 HTML | `html` | 简单单页、落地页 | 单个 `index.html` |
| 原生多文件 | `multi_file` | 中等复杂度页面 | `index.html` + `style.css` + `script.js` |
| Vue 工程 | `vue_project` | 多页面、复杂交互、组件化项目 | 完整 Vue 3 + Vite + Router 工程 |

### Vue 工程模式亮点

- **预置脚手架**：首次生成前自动复制标准 Vue 模板到 `vue_project_{appId}/`，AI 只需编写业务代码，显著缩短等待时间
- **Agent 工具调用**：AI 通过文件读写/修改/删除工具操作项目，支持多轮自动续跑
- **自动构建**：生成完成后执行 `npm install` + `npm run build`，预览与部署使用 `dist` 目录

---

## AI 工作流（LangGraph4j）

代码生成不是单次 LLM 调用，而是由 **LangGraph4j 工作流** 编排的多步骤流水线：

```
用户消息
  → 图片意图识别（是否需要收集图片）
  → 图片收集（搜索 / Logo / 插画 / 图表）
  → 提示词增强
  → 生成类型路由
  → 代码生成（LangChain4j + 工具调用）
  → 项目构建（Vue 模式：npm build）
  → 完成
```

### 图片资源能力

当用户需求涉及图片时，工作流会自动：

- 判断是否需要图片、定向还是全量收集
- 搜索内容配图、生成 Logo、获取插画、绘制 Mermaid 图表
- 将图片资源注入到增强后的提示词中，供代码生成使用

### Vue Agent 工具

Vue 工程模式下，AI 可调用以下工具：

| 工具 | 作用 |
|------|------|
| `writeFile` | 创建或重写文件 |
| `modifyFile` | 局部修改已有文件 |
| `readFile` | 读取文件内容 |
| `readDir` | 查看目录结构 |
| `deleteFile` | 删除文件 |

单轮工具调用有上限（默认 5 次），超出后系统会 **自动捕获并续跑**，避免生成中断。

---

## 技术栈

### 后端

- Java 21 + Spring Boot 3.5
- LangChain4j 1.14（AI 对话、工具调用、流式输出）
- LangGraph4j（工作流编排）
- MyBatis-Flex + MySQL
- Redis + Spring Session（会话与缓存）
- Redisson（分布式限流）
- Selenium（部署后自动截图生成封面）
- 腾讯云 COS（对象存储，可选）
- Knife4j / OpenAPI 3（API 文档）

### 前端

- Vue 3 + TypeScript + Vite
- Ant Design Vue
- Pinia + Vue Router
- Axios + SSE 流式通信
- Markdown 渲染（对话展示）

---

## 项目结构

```
quick-code/
├── src/main/java/org/example/quickcode/
│   ├── ai/                    # AI 服务、工具、路由
│   ├── config/                # Spring 配置（模型、Redis、COS 等）
│   ├── controller/            # REST API
│   ├── core/                  # 代码生成核心（解析、保存、流式处理、脚手架）
│   ├── langgraph4j/           # LangGraph 工作流与节点
│   ├── service/               # 业务服务
│   └── ...
├── src/main/resources/
│   ├── prompt/                # 各场景 System Prompt
│   ├── vue-scaffold/          # Vue 预置脚手架模板
│   └── application.yml
├── quick-code-frontend/       # Vue 3 前端
├── sql/                       # 数据库建表脚本
└── tmp/                       # 运行时产物（代码输出、部署文件，gitignore）
    ├── code_output/           # 生成的源码：{type}_{appId}/
    └── code_deploy/           # 部署产物：{deployKey}/
```

---

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.9+
- Node.js 18+（Vue 工程构建需要）
- MySQL 8+
- Redis 6+
- 可访问的 OpenAI 兼容 API（如智谱 GLM 等）

### 1. 初始化数据库

```bash
mysql -u root -p < sql/creat_table.sql
```

> 注意：`application.yml` 中默认数据库名为 `code_generation`，建表脚本中为 `quick_code`，请根据实际情况统一配置。

### 2. 配置后端

复制并编辑本地配置（该文件已在 `.gitignore` 中）：

```bash
# 在 src/main/resources/ 下创建 application-local.yml
```

示例配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/quick_code
    username: root
    password: your_password
  data:
    redis:
      host: localhost
      port: 6379

# HTML / 多文件生成模型
langchain4j:
  open-ai:
    streaming-chat-model:
      base-url: https://open.bigmodel.cn/api/paas/v4/
      api-key: your-api-key
      model-name: glm-4-flash
      max-tokens: 8192
      temperature: 0.7
    # 生成类型路由模型
    routing-chat-model:
      base-url: https://open.bigmodel.cn/api/paas/v4/
      api-key: your-api-key
      model-name: glm-4-flash
    # Vue Agent 推理模型（需支持工具调用）
    reasoning-streaming-chat-model:
      base-url: https://open.bigmodel.cn/api/paas/v4/
      api-key: your-api-key
      model-name: glm-4.5-air
      max-tokens: 16384
      temperature: 0.2

# 腾讯云 COS（可选，用于头像上传、截图存储）
cos:
  client:
    host: https://your-bucket.cos.region.myqcloud.com
    secret-id: your-secret-id
    secret-key: your-secret-key
    region: ap-guangzhou
    bucket: your-bucket
```

### 3. 启动后端

```bash
mvn spring-boot:run
```

后端默认地址：`http://localhost:8123/api`

API 文档：`http://localhost:8123/api/doc.html`

### 4. 启动前端

```bash
cd quick-code-frontend
npm install
npm run dev
```

前端环境变量（可在 `quick-code-frontend/.env.development` 中配置）：

```env
VITE_API_BASE_URL=http://localhost:8123/api
VITE_DEPLOY_DOMAIN=http://localhost:8123/api/deploy
```

---

## 典型使用流程

### 创建并生成应用

1. 注册 / 登录
2. 在首页输入应用描述，例如：「做一个咖啡品牌官网，包含首页、产品介绍、关于我们」
3. 系统自动创建应用并跳转到对话页
4. AI 工作流执行：路由 → 生成 → 构建 → 预览
5. 右侧 iframe 实时展示生成结果

### 继续修改

1. 在对话区输入修改需求，例如：「把导航栏改成深色，首页加一张轮播图」
2. AI 读取已有项目结构，增量修改对应文件
3. Vue 模式修改后自动重新构建并刷新预览

### 部署应用

1. 在对话页点击「部署」
2. 系统复制代码到部署目录（Vue 项目先 build 再部署 `dist`）
3. 返回可访问 URL，并异步生成应用封面截图

---

## 关键 API

| 接口 | 方法 | 说明 |
|------|------|------|
| `/app/add` | POST | 创建应用 |
| `/app/chat/gen/code` | GET (SSE) | 对话式代码生成 |
| `/app/deploy` | POST | 部署应用 |
| `/app/download/{appId}` | GET | 下载源码 |
| `/static/{type}_{appId}/**` | GET | 预览生成产物 |
| `/deploy/{deployKey}/**` | GET | 访问已部署应用 |

---

## 代码输出目录

每个应用的生成产物独立存放，互不干扰：

```
tmp/code_output/
├── html_{appId}/              # HTML 模式
├── multi_file_{appId}/        # 多文件模式
└── vue_project_{appId}/       # Vue 工程模式
    ├── src/
    ├── package.json
    └── dist/                  # 构建产物
```

Vue 脚手架模板源文件位于 `src/main/resources/vue-scaffold/`，**只读**；运行时复制到上述目录后再由 AI 修改。

---

## 开发与测试

```bash
# 后端编译
mvn compile

# 运行单元测试
mvn test

# 前端构建
cd quick-code-frontend && npm run build
```
