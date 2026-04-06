package com.seckill.order.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sys_order") // ShardingSphere 会自动路由到 sys_order_0 或 sys_order_1
public class Order implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId // 不使用自增，我们用雪花算法+基因算法手动生成
    private Long orderId;

    private Long userId;

    private Long productId;

    private BigDecimal amount;

    private Integer status;

    private LocalDateTime createTime;
}
