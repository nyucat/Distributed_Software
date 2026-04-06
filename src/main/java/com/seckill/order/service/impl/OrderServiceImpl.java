package com.seckill.order.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seckill.order.entity.Order;
import com.seckill.order.mapper.OrderMapper;
import com.seckill.order.mq.OrderMessage;
import com.seckill.order.service.OrderService;
import com.seckill.product.entity.Product;
import com.seckill.product.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private ProductService productService;

    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    private static final String BOUGHT_SET_PREFIX = "seckill:bought:";
    private static final String TOPIC_ORDER = "seckill-orders";

    @Override
    public String seckill(Long productId, Long userId) {
        String stockKey = STOCK_KEY_PREFIX + productId;
        String boughtKey = BOUGHT_SET_PREFIX + productId;

        // 1. 幂等性：判断用户是否已经抢购过该商品 (利用 Redis Set)
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(boughtKey, String.valueOf(userId));
        if (Boolean.TRUE.equals(isMember)) {
            return "您已经参与过该商品的秒杀，不能重复购买！";
        }

        // 2. Redis 预扣减库存 (保证不超卖)
        Long stock = stringRedisTemplate.opsForValue().decrement(stockKey);
        if (stock != null && stock < 0) {
            // 如果小于 0，说明库存不足，恢复刚刚扣减的 1 个单位，然后返回失败
            stringRedisTemplate.opsForValue().increment(stockKey);
            return "手慢了，商品已售罄！";
        }

        // 3. 将用户加入已购买集合 (防止多次快速点击导致重复下单)
        stringRedisTemplate.opsForSet().add(boughtKey, String.valueOf(userId));

        // 4. 基因算法生成订单 ID：
        // 我们的分库规则是按 user_id 分库，分表是按 order_id 分表。
        // 为了支持 "按用户ID或订单ID都能查询" 的需求，我们将 user_id 的末尾信息融合进 order_id 中。
        // ShardingSphere 的路由规则为：ds_{user_id % 2}.sys_order_{order_id % 2}
        // 这里我们把 order_id 的最后一位强行设置为 user_id 的最后一位，这样用 order_id % 2 同样能算出准确的库和表！
        long snowflakeId = IdUtil.getSnowflake(1, 1).nextId();
        long userGene = userId % 2; // 获取最后 1 位的基因 (0 或 1)
        long orderId = (snowflakeId << 1) | userGene; // 左移一位然后把基因放到最低位

        // 5. 构造消息，发送到 RocketMQ 进行削峰填谷，异步下单
        OrderMessage msg = new OrderMessage();
        msg.setOrderId(orderId);
        msg.setUserId(userId);
        msg.setProductId(productId);
        
        rocketMQTemplate.convertAndSend(TOPIC_ORDER, msg);
        
        log.info("秒杀请求已进入队列，等待异步处理，orderId={}, userId={}, productId={}", orderId, userId, productId);
        return "秒杀成功，正在排队生成订单...";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createOrder(Long orderId, Long userId, Long productId) {
        // 二次检查幂等：由于前面已经通过 Redis 控制了同一用户同一商品只能下单一次，所以理论上不会重复。
        // 但为了安全，也可以通过查询数据库订单表防重。

        // 1. 扣减数据库实际库存 (乐观锁机制保证不超卖)
        Product product = productService.getById(productId);
        if (product == null || product.getStock() <= 0) {
            log.error("创建订单失败，数据库商品库存不足。orderId={}", orderId);
            return;
        }

        // 实际业务中应该用 update sys_product set stock = stock - 1 where product_id = ? and stock > 0
        // 这里为了简化，我们依然调用 ProductService.deductStock，但改造一下它以确保原子性
        boolean deductSuccess = productService.deductStock(productId, 1);
        if (!deductSuccess) {
            log.error("创建订单失败，扣减数据库库存失败。orderId={}", orderId);
            return; // 扣减失败，放弃下单
        }

        // 2. 创建订单并插入数据库
        Order order = new Order();
        order.setOrderId(orderId);
        order.setUserId(userId);
        order.setProductId(productId);
        order.setAmount(product.getPrice()); // 实际应为秒杀价
        order.setStatus(0); // 待支付
        order.setCreateTime(LocalDateTime.now());

        this.save(order);
        log.info("订单创建成功并写入数据库！orderId={}", orderId);
    }
}
