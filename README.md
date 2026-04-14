# Distributed Seckill System

## 1. 项目简介

本项目是一个基于 Java 的分布式秒杀系统示例，聚焦高并发下单链路的核心问题：

- Redis + Lua 实现秒杀库存原子预扣减与防重复购买
- RocketMQ 实现异步削峰与最终一致性
- MySQL 分库分表设计（`seckill_0` / `seckill_1` + `sys_order_0` / `sys_order_1`）
- Nginx 负载均衡到双应用实例（`app1` / `app2`）
- Redis 实现轻量 Raft 选主，限制写操作仅由 leader 执行（可开关）

当前仓库为**单 Maven 模块单体应用**，通过容器编排实现“多实例部署”。

---

## 2. 核心技术栈

### 2.1 后端框架

- Java 8
- Spring Boot 2.6.13
- Spring Cloud 2021.0.5
- Spring Cloud Alibaba 2021.0.5.0
- MyBatis-Plus 3.5.2

### 2.2 中间件与基础设施

- MySQL 8.0.32
- Redis 7
- RocketMQ 4.9.4（`rocketmq-spring-boot-starter 2.2.2`）
- Nginx 1.23

### 2.3 安全与工具

- Spring Security（密码加密：`BCryptPasswordEncoder`）
- JWT（`jjwt 0.9.1`）
- Hutool
- Lombok

---

## 3. 架构与请求链路

### 3.1 部署拓扑（docker-compose）

```text
Client
  -> Nginx:80
      -> app1:8081
      -> app2:8082

app(同构实例)
  -> MySQL Master/Slave
  -> Redis
  -> RocketMQ(NameServer + Broker)
```

### 3.2 秒杀下单主链路

1. 用户调用 `POST /api/order/seckill`
2. 服务执行 Redis Lua：
   - 校验是否重复购买（`SISMEMBER`）
   - 校验库存并原子扣减（`GET` + `DECR`）
   - 记录已购用户（`SADD`）
3. 生成订单号（雪花 ID + user 基因位）
4. 发送 RocketMQ 消息到 `seckill-orders`
5. 消费者侧进行 leader 校验（Raft 优化开关）
6. 写入 Raft 日志（Redis List），执行落库下单
7. 扣减 DB 库存、创建订单；失败则回滚 Redis 预扣减

### 3.3 支付更新链路

1. 调用 `POST /api/order/pay`
2. 发送支付消息到 `seckill-order-pay`
3. 消费者校验 leader 并记录 Raft 日志
4. 执行本地事务，将订单状态从 `0` 更新为 `1`

---

## 4. 项目结构说明

```text
Distributed_Software/
├─ src/
│  ├─ main/java/com/seckill/
│  │  ├─ user/      # 用户域：登录、注册、查询、JWT
│  │  ├─ product/   # 商品域：商品查询、库存扣减
│  │  └─ order/     # 订单域：秒杀、支付、MQ、Raft
│  └─ main/resources/
│     ├─ application.yml
│     └─ mapper/
├─ sql/
│  └─ schema.sql    # 分库分表建表脚本
├─ mysql/
│  ├─ master/       # 主库配置与初始化
│  └─ slave/        # 从库配置与初始化
├─ nginx/
│  ├─ conf/nginx.conf
│  └─ html/
├─ Dockerfile
├─ docker-compose.yml
└─ pom.xml
```

---

## 5. 本地运行指南

### 5.1 前置要求

- Docker / Docker Compose
- JDK 8（仅本地直接运行 jar 时需要）
- Maven 3.8+（仅本地源码构建时需要）

### 5.2 一键启动（推荐）

在项目根目录执行：

```bash
docker compose up -d --build
```

启动后主要端口：

- `80`：Nginx 入口
- `8081`：应用实例 1
- `8082`：应用实例 2
- `3306`：MySQL 主库
- `3307`：MySQL 从库
- `6379`：Redis
- `9876`：RocketMQ NameServer

### 5.3 关键环境变量（应用）

`application.yml` / `docker-compose.yml` 中涉及：

- `SERVER_PORT`（默认 `8081`）
- `DB_MASTER_HOST`
- `DB_PASSWORD`
- `REDIS_HOST`
- `ROCKETMQ_HOST`
- `RAFT_OPTIMIZATION_ENABLED`（默认 `false`）
- `RAFT_LEADER_LEASE_SECONDS`（默认 `8`）

---

## 6. API 接口清单

统一返回结构为 `Result<T>`。

### 6.1 用户接口

#### `POST /api/user/login`

- 入参：`LoginDTO`（`username`, `password`）
- 出参：`LoginVO`（`token`, `userInfo`）

#### `POST /api/user/register`

- 入参：`RegisterDTO`（`username`, `password`, `phone`）
- 出参：`Boolean`

#### `GET /api/user/{id}`

- 出参：`UserVO`

### 6.2 商品接口

#### `GET /api/product/{productId}`

- 出参：`Product`
- 逻辑：先查 Redis 缓存，未命中回源 DB

#### `GET /api/product/seckill/list`

- 当前实现：占位返回（后续可接入真实商品列表查询）

### 6.3 订单接口

#### `POST /api/order/seckill`

- 参数：`productId`, `userId`（当前为压测简化，真实场景应从 JWT 获取）
- 返回：秒杀受理结果文本

#### `GET /api/order/{orderId}`

- 返回：订单详情

#### `POST /api/order/pay`

- 参数：`orderId`, `userId`
- 返回：支付请求受理结果文本（异步更新状态）

---

## 7. 数据模型与分片设计

### 7.1 核心表

- `sys_user`
- `sys_product`
- `sys_order_0`
- `sys_order_1`

### 7.2 数据库划分

- 库：`seckill_0`、`seckill_1`
- 订单表按库内分表：`sys_order_0`、`sys_order_1`
- 目标：支持高并发下订单写入扩展能力

### 7.3 订单号路由策略

订单 ID 在雪花 ID 基础上融合 `userId % 2` 基因位，提升路由可预测性：

- 便于配合分片规则进行按用户/按订单的路由定位
- 降低跨分片查询与写入不均风险

---

## 8. 消息与一致性策略

### 8.1 消息主题

- `seckill-orders`：异步创建订单
- `seckill-order-pay`：异步支付状态更新

### 8.2 幂等与回滚

- 订单创建前先做订单主键幂等检查
- 库存扣减失败或写库失败时回滚 Redis 预扣减与已购标记
- 支付状态更新使用条件更新（`status = 0 -> 1`）防止重复更新

### 8.3 Raft 轻量优化（可选）

- Redis Key 控制 leader 租约与 term
- 非 leader 消费者拒绝执行写操作并抛异常触发重试
- 写命令追加到 Redis Raft 日志，便于观测

---

## 9. 已实现能力与当前边界

### 9.1 已实现

- 秒杀核心并发控制（Redis Lua）
- 异步下单与支付状态更新（RocketMQ）
- 基础用户登录注册与 JWT 生成
- 双实例 + Nginx 负载均衡
- 数据初始化脚本与容器化部署

### 9.2 当前边界 / 待完善

- 还未提供完整压测脚本与指标看板
- 商品秒杀列表接口目前为占位实现
- 安全链路（JWT 鉴权拦截）未完全闭环到订单接口
- 分片规则配置细节建议补充独立文档（当前以代码注释+SQL为主）
- 缺少自动化测试与 CI 流水线

---

## 10. 快速联调示例

### 10.1 注册

```bash
curl -X POST "http://localhost/api/user/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"u1\",\"password\":\"123456\",\"phone\":\"13800000000\"}"
```

### 10.2 登录

```bash
curl -X POST "http://localhost/api/user/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"u1\",\"password\":\"123456\"}"
```

### 10.3 秒杀下单

```bash
curl -X POST "http://localhost/api/order/seckill?productId=1&userId=1"
```

### 10.4 支付订单

```bash
curl -X POST "http://localhost/api/order/pay?orderId=<orderId>&userId=1"
```

---

## 11. 许可证

当前仓库未声明独立 LICENSE 文件；如需开源分发，建议补充许可证声明。