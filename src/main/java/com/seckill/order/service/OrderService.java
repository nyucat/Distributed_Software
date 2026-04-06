package com.seckill.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seckill.order.entity.Order;

public interface OrderService extends IService<Order> {

    /**
     * 高并发秒杀下单接口 (核心)
     * 返回提示信息 (排队中、库存不足等)
     */
    String seckill(Long productId, Long userId);

    /**
     * 实际创建订单逻辑 (由 RocketMQ 消费者调用)
     */
    void createOrder(Long orderId, Long userId, Long productId);

    /**
     * 模拟订单支付，发送可靠半消息保证分布式事务 (订单支付 + 状态更新)
     */
    String payOrder(Long orderId, Long userId);

    /**
     * 执行本地支付事务
     */
    void executeLocalPayTransaction(Long orderId, Long userId);
}
