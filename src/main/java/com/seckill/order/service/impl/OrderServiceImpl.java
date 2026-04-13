package com.seckill.order.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seckill.order.entity.Order;
import com.seckill.order.mapper.OrderMapper;
import com.seckill.order.mq.OrderMessage;
import com.seckill.order.mq.OrderPayMessage;
import com.seckill.order.service.OrderService;
import com.seckill.product.entity.Product;
import com.seckill.product.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
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
    private static final String TOPIC_ORDER_PAY = "seckill-order-pay";
    private static final String LUA_SECKILL_RESERVE =
            "if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then return -2 end; " +
            "local stock = tonumber(redis.call('GET', KEYS[1]) or '-1'); " +
            "if stock <= 0 then return -1 end; " +
            "redis.call('DECR', KEYS[1]); " +
            "redis.call('SADD', KEYS[2], ARGV[1]); " +
            "return 1;";

    @Override
    public String seckill(Long productId, Long userId) {
        String stockKey = STOCK_KEY_PREFIX + productId;
        String boughtKey = BOUGHT_SET_PREFIX + productId;

        // 1. 原子预扣减 + 幂等标记，避免并发下出现窗口期不一致。
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(LUA_SECKILL_RESERVE);
        script.setResultType(Long.class);
        Long reserveResult = stringRedisTemplate.execute(
                script, Arrays.asList(stockKey, boughtKey), String.valueOf(userId)
        );
        if (reserveResult == null) {
            return "系统繁忙，请稍后重试";
        }
        if (reserveResult == -2L) {
            return "您已经参与过该商品的秒杀，不能重复购买！";
        }
        if (reserveResult == -1L) {
            return "手慢了，商品已售罄！";
        }

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
        // MQ 至少一次投递，先做订单幂等检查，重复消息直接视为成功。
        Order existed = this.getById(orderId);
        if (existed != null) {
            log.info("重复下单消息，订单已存在，跳过。orderId={}", orderId);
            return;
        }

        // 1. 扣减数据库实际库存 (乐观锁机制保证不超卖)
        Product product = productService.getById(productId);
        if (product == null || product.getStock() <= 0) {
            log.error("创建订单失败，数据库商品库存不足。orderId={}", orderId);
            rollbackRedisReservation(productId, userId);
            return;
        }

        // 实际业务中应该用 update sys_product set stock = stock - 1 where product_id = ? and stock > 0
        // 这里为了简化，我们依然调用 ProductService.deductStock，但改造一下它以确保原子性
        boolean deductSuccess = productService.deductStock(productId, 1);
        if (!deductSuccess) {
            log.error("创建订单失败，扣减数据库库存失败。orderId={}", orderId);
            rollbackRedisReservation(productId, userId);
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

        boolean saved = this.save(order);
        if (!saved) {
            log.error("创建订单失败，订单写库失败。orderId={}", orderId);
            rollbackRedisReservation(productId, userId);
            throw new RuntimeException("订单写库失败");
        }

        log.info("订单创建成功并写入数据库！orderId={}", orderId);
    }

    @Override
    public String payOrder(Long orderId, Long userId) {
        Order order = this.getById(orderId);
        if (order == null) {
            return "订单不存在";
        }
        if (!userId.equals(order.getUserId())) {
            return "无权支付该订单";
        }
        if (order.getStatus() != null && order.getStatus() == 1) {
            return "订单已支付，请勿重复操作";
        }
        if (order.getStatus() != null && order.getStatus() == 2) {
            return "订单已取消，无法支付";
        }

        OrderPayMessage payMessage = new OrderPayMessage();
        payMessage.setOrderId(orderId);
        payMessage.setUserId(userId);
        payMessage.setStatus(1);
        rocketMQTemplate.convertAndSend(TOPIC_ORDER_PAY, payMessage);
        log.info("支付消息已发送，orderId={}, userId={}", orderId, userId);
        return "支付请求已受理，订单状态更新中";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void executeLocalPayTransaction(Long orderId, Long userId) {
        Order order = this.getById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在，orderId=" + orderId);
        }
        if (!userId.equals(order.getUserId())) {
            throw new RuntimeException("支付用户与订单不匹配，orderId=" + orderId);
        }
        if (order.getStatus() != null && order.getStatus() == 1) {
            log.info("支付消息重复消费，订单已支付，忽略。orderId={}", orderId);
            return;
        }
        if (order.getStatus() != null && order.getStatus() == 2) {
            throw new RuntimeException("订单已取消，禁止支付，orderId=" + orderId);
        }

        boolean updated = this.lambdaUpdate()
                .eq(Order::getOrderId, orderId)
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, 0)
                .set(Order::getStatus, 1)
                .update();
        if (!updated) {
            // 极端并发下这里可能被其他线程先一步更新，再次校验实现幂等。
            Order latest = this.getById(orderId);
            if (latest != null && latest.getStatus() != null && latest.getStatus() == 1) {
                log.info("订单状态已由并发流程更新为已支付，忽略。orderId={}", orderId);
                return;
            }
            throw new RuntimeException("订单状态更新失败，orderId=" + orderId);
        }
        log.info("订单支付成功，状态已更新为已支付。orderId={}", orderId);
    }

    private void rollbackRedisReservation(Long productId, Long userId) {
        String stockKey = STOCK_KEY_PREFIX + productId;
        String boughtKey = BOUGHT_SET_PREFIX + productId;
        stringRedisTemplate.opsForValue().increment(stockKey);
        stringRedisTemplate.opsForSet().remove(boughtKey, String.valueOf(userId));
        log.info("已回滚 Redis 预扣减与幂等标记。productId={}, userId={}", productId, userId);
    }
}