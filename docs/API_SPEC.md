# 前后端 API 交互规范文档 (RESTful & SSE)

本文档定义了知识分享博客系统 (`blogs`) 前后端交互的标准协议、数据格式、状态码约定及核心接口定义。

---

## 一、 通用协议与数据格式

### 1. 基础约定
- **基准路径 (Base URL)**: `/api/v1`
- **请求数据格式**: `Content-Type: application/json; charset=UTF-8`
- **鉴权方式**: HTTP Header 携带 JWT Token
    - `Authorization: Bearer <your_jwt_token>`

### 2. 统一响应结构 (`Result<T>`)

除 SSE（流式响应）外，所有 HTTP 接口必须返回统一的 JSON 格式：

#### 成功响应格式
```json
{
  "code": 200,
  "message": "操作成功",
  "data": { ... },
  "timestamp": 1721732646000
}
##### 失败响应格式
{
  "code": 400,
  "message": "参数校验失败 / 校验异常信息",
  "data": null,
  "timestamp": 1721732646000
}
### 3. 状态码约定
状态码	意义      	说明
200  	成功	        处理请求成功
400	    错误的请求	请求参数有错误或业务校验失败
401  	未经授权	    未登录或 JWT 令牌已过期
403	    禁忌	        权限不足（如非管理员尝试访问管理端接口）
404 	未找到	    资源不存在
429 	请求过多	    触发限流（特别是 AI 问答接口频率过高）
500 	错误  	    系统内部错误 / 未捕获异常

3.分页请求与响应规范
对于所有列表查询接口，采用统一的分页入参和出参：

分页请求参数 (Query Params)
pageNum: 当前页码 (从1开始，默认1)
pageSize: 每页条数 (默认10，最大限制100)
分页响应数据结构
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "list": [ ... ],
    "total": 45,
    "pageNum": 1,
    "pageSize": 10,
    "pages": 5
  },
  "timestamp": 1721732646000
}

二、 核心 API 接口定义
1. 认证与用户模块 (Auth & User)
HTTP    方法接口路径              权限要求接口说明
POST    /api/v1/auth/register   公开用户注册
POST    /api/v1/auth/login      公开用户登录（返回 JWT Token）
GET     /api/v1/user/info       需登录获取当前登录用户信息

登录接口示例 (POST /api/v1/auth/login)
Request Body:

JSON
{
  "username": "admin",
  "password": "raw_password_123"
}
Response Data:

JSON
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "userInfo": {
      "id": 1,
      "username": "admin",
      "nickname": "系统管理员",
      "avatar": "https://...",
      "role": "ADMIN"
    }
  }
}

HTTP    方法,                   接口路径,      权限要求,       接口说明
GET     ,/api/v1/categories     ,公开       获取所有启用版块分类（支持树状层级）
POST    ,/api/v1/admin/categories ,管理员    ,[管理端] 创建新版块/分类
PUT     ,/api/v1/admin/categories/{id},管理员  ,[管理端] 修改版块/分类
DELETE  ,/api/v1/admin/categories/{id},管理员  ,[管理端] 删除指定版块/分类

创建分类接口示例 (POST /api/v1/admin/categories)
Request Body:
{
  "parentId": 0,
  "name": "Java后端",
  "slug": "java",
  "description": "Java生态与Spring体系知识分享",
  "icon": "icon-java",
  "sortOrder": 10
}


HTTP方法,        接口路径,           权限要求,             接口说明
GET       ,/api/v1/articles,        公开,               分页获取文章列表（支持按 categoryId/tagId/keyword 筛选）
GET       ,/api/v1/articles/{id},   公开,               获取文章详情（Markdown & HTML）
POST      ,/api/v1/articles,        需登录,              发布新文章
PUT       ,/api/v1/articles/{id},   作者/管理员,          更新文章内容
DELETE    ,/api/v1/articles/{id},   作者/管理员,          删除文章 (逻辑删除)

发布文章示例 (POST /api/v1/articles)
Request Body:
{
  "title": "Spring Boot 3.4 与 Java 21 最佳实践",
  "categoryId": 2,
  "summary": "本文将介绍如何基于 Spring Boot 3.4 快速搭建后台服务...",
  "contentMd": "# 标题\n内容正文...",
  "coverImage": "https://...",
  "tagIds": [1, 3],
  "status": 1
}

4. AI 问答交互模块 (AI Chat - SSE 流式推送)
针对 AI 问答，为了实现类似 ChatGPT 的打字机效果，采用 Server-Sent Events (SSE) 交互标准。

HTTP 方法,          接口路径,                  权限要求,         说明
GET          / POST,/api/v1/ai/chat/stream,   需登录 /       可限流,流式对话接口 (text/event-stream)
GET,        /api/v1/ai/sessions,             需登录,         获取用户的 AI 历史对话列表
GET,        /api/v1/ai/sessions/{sessionId}/messages,需登录    ,获取指定会话的历史消息明细

SSE 流式交互协议说明
Header: Accept: text/event-stream

Event 数据流格式:

event: message
data: {"content": "您", "finish": false}
event: message
data: {"content": "好！", "finish": false}
event: message
data: {"content": "", "finish": true, "sessionId": 1024}