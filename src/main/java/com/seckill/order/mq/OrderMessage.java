package com.seckill.order.mq;

import lombok.Data;

@Data
public class OrderMessage {
    private Long orderId;
    private Long userId;
    private Long productId;
}
