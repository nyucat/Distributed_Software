package com.seckill.order.mq;

import lombok.Data;

@Data
public class OrderPayMessage {
    private Long orderId;
    private Long userId;
    private Integer status; // 1: 支付成功
}