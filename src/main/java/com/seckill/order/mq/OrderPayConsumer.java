package com.seckill.order.mq;

import cn.hutool.json.JSONUtil;
import com.seckill.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(topic = "seckill-order-pay", consumerGroup = "seckill-pay-group")
public class OrderPayConsumer implements RocketMQListener<OrderPayMessage> {

    @Autowired
    private OrderService orderService;

    @Override
    public void onMessage(OrderPayMessage message) {
        log.info("RocketMQ 收到支付消息: {}", JSONUtil.toJsonStr(message));
        try {
            orderService.executeLocalPayTransaction(message.getOrderId(), message.getUserId());
        } catch (Exception e) {
            log.error("RocketMQ 消费支付消息失败: {}", JSONUtil.toJsonStr(message), e);
            throw new RuntimeException("订单支付状态更新失败", e);
        }
    }
}
