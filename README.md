一、 系统架构草图（服务拆分）
采用微服务架构，将系统拆分为四大核心服务，通过服务注册与发现进行协调。
1. 服务拓扑结构
text
[客户端层] 
    ↓
[网关层 (Gateway)] —— 限流、路由、鉴权
    ↓
┌─────────────────────────────────────────┐
│  [核心业务服务集群]                     │
│  1. 用户服务 (User Service)             │
│  2. 商品服务 (Product Service)          │
│  3. 订单服务 (Order Service)            │
│  4. 库存服务 (Inventory Service)        │
└─────────────────────────────────────────┘
    ↓
[中间件层]
┌─────────────────────────────────────────┐
│  1. 注册中心 (Nacos/Eureka)              │
│  2. 配置中心 (Nacos/Apollo)               │
│  3. 服务容错 (Sentinel/Hystrix)          │
│  4. 消息队列 (RabbitMQ/Kafka) —— 削峰填谷│
└─────────────────────────────────────────┘
    ↓
[数据层]
┌─────────────────────────────────────────┐
│  1. 关系型数据库 (MySQL) —— 核心业务数据  │
│  2. 缓存数据库 (Redis) —— 秒杀缓存/分布式锁│
│  3. 搜索引擎 (Elasticsearch) —— 商品检索  │
└─────────────────────────────────────────┘
2. 核心服务职责
表格
服务名称	核心职责	关键功能
用户服务	用户身份认证与管理	登录、注册、用户信息查询、风控校验
商品服务	商品信息管理	商品详情查询、上下架、分类 / 搜索
库存服务	库存核心控制	库存预扣减、库存回滚、库存查询、秒杀资格判断
订单服务	订单生命周期	创建订单、支付状态同步、订单查询 / 关闭
二、 定义各服务 API 接口（RESTful）
采用 RESTful 风格设计，返回标准 JSON 格式。
1. 用户服务 (User-Service)
表格
接口	方法	路径	入参	出参	说明
登录	POST	/api/user/login	username/password	token, userInfo	返回 JWT Token
获取信息	GET	/api/user/{id}	-	UserVO	获取用户详情
2. 商品服务 (Product-Service)
表格
接口	方法	路径	入参	出参	说明
列表查询	GET	/api/products	pageNum, size	Page<ProductVO>	普通商品列表
详情查询	GET	/api/product/{id}	-	ProductDetailVO	包含库存信息
秒杀商品	GET	/api/product/seckill/list	-	List<SeckillProductVO>	获取秒杀专场商品
3. 库存服务 (Inventory-Service)
表格
接口	方法	路径	入参	出参	说明
扣减库存	POST	/api/inventory/deduct	productId, quantity	Boolean	核心接口，需加锁
恢复库存	POST	/api/inventory/restore	productId, quantity	Boolean	订单取消回调
查询库存	GET	/api/inventory/{productId}	-	stockNum	实时库存
4. 订单服务 (Order-Service)
表格
接口	方法	路径	入参	出参	说明
创建订单	POST	/api/order	productId, address	orderId	调用库存扣减接口
查询订单	GET	/api/order/{id}	-	OrderVO	根据订单号查询
支付回调	POST	/api/order/pay/callback	orderId, status	-	支付结果更新
三、 数据库 ER 图
核心包含四张表，关系为：一个用户对应多个订单，一个订单对应一个商品及库存记录。
1. 用户表 (sys_user)
表格
字段名	类型	备注
user_id	BIGINT	主键
username	VARCHAR	账号
password	VARCHAR	加密密码
phone	VARCHAR	手机号
create_time	DATETIME	创建时间
2. 商品表 (sys_product)
表格
字段名	类型	备注
product_id	BIGINT	主键
product_name	VARCHAR	商品名称
price	DECIMAL	原价
stock	INT	总库存 (冗余，建议主要查库存表)
status	TINYINT	上下架状态
pic_url	VARCHAR	商品图片
3. 库存表 (sys_inventory)
表格
字段名	类型	备注
id	BIGINT	主键
product_id	BIGINT	关联商品 ID
available_stock	INT	可用库存 (核心字段)
locked_stock	INT	锁定库存
update_time	DATETIME	更新时间
4. 订单表 (sys_order)
表格
字段名	类型	备注
order_id	BIGINT	主键 (订单号)
user_id	BIGINT	下单用户
product_id	BIGINT	购买商品
amount	DECIMAL	订单金额
status	TINYINT	状态 (0 - 待支付，1 - 已支付，2 - 已取消)
create_time	DATETIME	创建时间
四、 技术栈选型说明
1. 编程语言与核心框架
后端语言：Java (生态成熟，并发处理强大) / Go (高并发性能极佳，秒杀场景首选)。
框架：
Spring Boot：快速开发微服务。
Spring Cloud Alibaba：一站式微服务解决方案。
注册配置中心：Nacos (替代 Eureka/Config，支持动态配置)。
服务网关：Spring Cloud Gateway (替代 Zuul，性能更好)。
服务容错：Sentinel (限流、熔断、降级核心组件)。
2. 数据库与缓存
主存储：MySQL 8.0。使用 InnoDB 引擎，分库分表（若数据量大），基于 product_id 做哈希分表。
缓存核心：Redis。
作用：秒杀瞬间流量巨大，直接请求数据库会挂掉。Redis 作为分布式缓存，预先加载库存数据。
数据结构：使用 String 存储库存数量；使用 Redisson 实现分布式锁 (RLock) 解决超卖问题。
搜索引擎：Elasticsearch。用于商品列表的模糊搜索、高亮及复杂条件查询。
3. 消息中间件
选型：RabbitMQ 或 Kafka。
秒杀削峰：用户请求先进入 MQ 队列，后端消费者缓慢消费，写入数据库。避免瞬时高并发压垮数据库。
异步解耦：订单创建与库存扣减通过 MQ 通信，减少接口响应时间。
4. 辅助工具
接口文档：Swagger / OpenAPI 3.0。
数据库持久层：MyBatis-Plus / JPA。
分布式事务：Seata (确保库存扣减和订单创建的原子性)。