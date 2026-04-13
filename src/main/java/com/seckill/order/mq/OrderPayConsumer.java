package com.seckill.order.mq;

import cn.hutool.json.JSONUtil;
import com.seckill.order.raft.LeadershipSnapshot;
import com.seckill.order.raft.RaftLeadershipService;
import com.seckill.order.raft.RaftLogService;
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
    @Autowired
    private RaftLeadershipService raftLeadershipService;
    @Autowired
    private RaftLogService raftLogService;

    @Override
    public void onMessage(OrderPayMessage message) {
        log.info("RocketMQ 收到支付消息: {}", JSONUtil.toJsonStr(message));
        try {
            LeadershipSnapshot leadership = raftLeadershipService.tryAcquireOrRenewLeadership();
            if (!leadership.isLeader()) {
                log.warn("当前实例不是 leader，拒绝执行支付写操作。nodeId={}, leaderId={}, term={}, orderId={}",
                        leadership.getNodeId(), leadership.getCurrentLeaderId(), leadership.getTerm(), message.getOrderId());
                throw new RuntimeException("当前节点非 leader，触发重试");
            }
            String command = "pay_order:orderId=" + message.getOrderId()
                    + ",userId=" + message.getUserId()
                    + ",status=" + message.getStatus();
            raftLogService.appendLog(leadership.getTerm(), command);
            orderService.executeLocalPayTransaction(message.getOrderId(), message.getUserId());
        } catch (Exception e) {
            log.error("RocketMQ 消费支付消息失败: {}", JSONUtil.toJsonStr(message), e);
            throw new RuntimeException("订单支付状态更新失败", e);
        }
    }
}
