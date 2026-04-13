package com.seckill.order.mq;

import cn.hutool.json.JSONUtil;
import com.seckill.order.service.OrderService;
import com.seckill.order.raft.LeadershipSnapshot;
import com.seckill.order.raft.RaftLeadershipService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(topic = "seckill-orders", consumerGroup = "seckill-group")
public class OrderConsumer implements RocketMQListener<OrderMessage> {

    @Autowired
    private OrderService orderService;
    @Autowired
    private RaftLeadershipService raftLeadershipService;

    @Override
    public void onMessage(OrderMessage message) {
        log.info("RocketMQ 收到秒杀下单消息: {}", JSONUtil.toJsonStr(message));
        try {
            LeadershipSnapshot leadership = raftLeadershipService.tryAcquireOrRenewLeadership();
            if (!leadership.isLeader()) {
                log.warn("当前实例不是 leader，拒绝执行订单写操作。nodeId={}, leaderId={}, term={}, orderId={}",
                        leadership.getNodeId(), leadership.getCurrentLeaderId(), leadership.getTerm(), message.getOrderId());
                throw new RuntimeException("当前节点非 leader，触发重试");
            }
            // 调用真正的下单业务
            orderService.createOrder(message.getOrderId(), message.getUserId(), message.getProductId());
        } catch (Exception e) {
            log.error("RocketMQ 消费秒杀消息失败: {}", JSONUtil.toJsonStr(message), e);
            // 抛出异常让 RocketMQ 自动重试
            throw new RuntimeException("订单消费失败", e);
        }
    }
}
