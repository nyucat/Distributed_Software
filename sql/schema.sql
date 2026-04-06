-- ==========================================
-- Database 0 (seckill_0)
-- ==========================================
CREATE DATABASE IF NOT EXISTS seckill_0 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE seckill_0;

-- 用户表
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `user_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` varchar(50) NOT NULL COMMENT '账号',
  `password` varchar(100) NOT NULL COMMENT '加密密码',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 商品表
DROP TABLE IF EXISTS `sys_product`;
CREATE TABLE `sys_product` (
  `product_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `product_name` varchar(100) NOT NULL COMMENT '商品名称',
  `price` decimal(10,2) NOT NULL COMMENT '原价',
  `stock` int(11) NOT NULL COMMENT '总库存',
  `status` tinyint(4) DEFAULT '1' COMMENT '上下架状态 (1-上架, 0-下架)',
  `pic_url` varchar(255) DEFAULT NULL COMMENT '商品图片',
  PRIMARY KEY (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- 插入一条测试数据用于演示秒杀和动静分离
INSERT INTO `sys_product` (`product_id`, `product_name`, `price`, `stock`, `status`) VALUES (1, 'iPhone 15 Pro Max', 9999.00, 100, 1);

-- 订单表分表 0 (sys_order_0)
DROP TABLE IF EXISTS `sys_order_0`;
CREATE TABLE `sys_order_0` (
  `order_id` bigint(20) NOT NULL COMMENT '订单号',
  `user_id` bigint(20) NOT NULL COMMENT '下单用户',
  `product_id` bigint(20) NOT NULL COMMENT '购买商品',
  `amount` decimal(10,2) NOT NULL COMMENT '订单金额',
  `status` tinyint(4) DEFAULT '0' COMMENT '状态 (0 - 待支付，1 - 已支付，2 - 已取消)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`order_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表_0';

-- 订单表分表 1 (sys_order_1)
DROP TABLE IF EXISTS `sys_order_1`;
CREATE TABLE `sys_order_1` LIKE `sys_order_0`;

-- ==========================================
-- Database 1 (seckill_1)
-- ==========================================
CREATE DATABASE IF NOT EXISTS seckill_1 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE seckill_1;

-- 订单表分表 0 (sys_order_0)
DROP TABLE IF EXISTS `sys_order_0`;
CREATE TABLE `sys_order_0` (
  `order_id` bigint(20) NOT NULL COMMENT '订单号',
  `user_id` bigint(20) NOT NULL COMMENT '下单用户',
  `product_id` bigint(20) NOT NULL COMMENT '购买商品',
  `amount` decimal(10,2) NOT NULL COMMENT '订单金额',
  `status` tinyint(4) DEFAULT '0' COMMENT '状态 (0 - 待支付，1 - 已支付，2 - 已取消)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`order_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表_0';

-- 订单表分表 1 (sys_order_1)
DROP TABLE IF EXISTS `sys_order_1`;
CREATE TABLE `sys_order_1` LIKE `sys_order_0`;
