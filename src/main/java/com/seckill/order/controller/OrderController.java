package com.seckill.order.controller;

import com.seckill.order.entity.Order;
import com.seckill.order.service.OrderService;
import com.seckill.user.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 核心秒杀接口
     */
    @PostMapping("/seckill")
    public Result<String> seckill(@RequestParam("productId") Long productId, @RequestParam("userId") Long userId) {
        try {
            // 在实际项目中，userId 应该从网关透传的 JWT Token 中获取，这里为了方便压测直接传递
            String resultMsg = orderService.seckill(productId, userId);
            return Result.success(resultMsg);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 按订单 ID 查询 (测试分库分表基因算法路由)
     */
    @GetMapping("/{orderId}")
    public Result<Order> getOrderById(@PathVariable("orderId") Long orderId) {
        Order order = orderService.getById(orderId);
        if (order != null) {
            return Result.success(order);
        }
        return Result.error("订单未找到");
    }

    /**
     * 模拟支付接口：通过 MQ 异步更新订单状态，保障支付链路最终一致
     */
    @PostMapping("/pay")
    public Result<String> pay(@RequestParam("orderId") Long orderId, @RequestParam("userId") Long userId) {
        try {
            return Result.success(orderService.payOrder(orderId, userId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
