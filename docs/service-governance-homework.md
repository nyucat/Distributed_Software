# 服务治理作业操作说明

## 1. 启动环境

```powershell
docker compose --profile full up -d --build
```

关键入口：

- Nacos 控制台：`http://localhost:8848/nacos`（默认无需登录）
- Gateway：`http://localhost:8080`
- 统一前端入口：`http://localhost`

## 2. 服务注册发现与配置

### 2.1 查看注册实例

在 Nacos 控制台查看服务：

- `seckill-app`（应有 `app1`、`app2` 两个实例）
- `seckill-gateway`

### 2.2 网关调用服务（动态路由）

通过网关访问后端接口：

```powershell
curl http://localhost:8080/api/product/1
curl http://localhost:8080/api/user/1
```

如果使用前端入口，`/api/*` 也会先经过 gateway。

### 2.3 动态配置刷新验证

1. 在 Nacos 新建 Data ID：`seckill-app.yaml`，Group：`DEFAULT_GROUP`
2. 配置内容示例：

```yaml
governance:
  message: "message-from-nacos-v1"
```

3. 查询当前值：

```powershell
curl http://localhost:8080/api/config/message
```

4. 修改 Nacos 中 `governance.message` 为新值，再次请求，验证动态生效。

## 3. 流量治理

### 3.1 限流（Gateway）

`gateway` 已配置 `RequestRateLimiter`：

- `replenishRate: 15`
- `burstCapacity: 30`

对 `/api/**` 生效，超限时返回 `429 Too Many Requests`。

### 3.2 熔断与降级（Gateway）

`gateway` 已配置 `CircuitBreaker`，降级地址：

- `/fallback/seckill`

当下游服务异常时，返回：

```json
{"code":503,"message":"Gateway circuit breaker fallback"}
```

### 3.3 应用层限流降级（Sentinel）

`/api/product/{id}` 已接入 Sentinel 注解：

- `blockHandler`：限流提示
- `fallback`：降级提示

可在 Sentinel 控制台配置规则后验证效果。

## 4. JMeter 压测建议

1. 线程组：`100~300` 并发用户
2. 循环次数：`50~200`
3. 目标 URL：`http://localhost:8080/api/product/1`
4. 观察指标：

- 吞吐量（Throughput）
- 平均响应时间（Average）
- 错误率（Error %）
- `429` 比例（限流生效）

## 5. 常用验证命令

```powershell
docker compose --profile full ps
docker logs seckill-gateway --tail 100
docker logs seckill-app1 --tail 100
docker logs seckill-app2 --tail 100
```
