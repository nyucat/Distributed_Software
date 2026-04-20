package com.seckill.product.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.seckill.product.entity.Product;
import com.seckill.product.service.ProductService;
import com.seckill.user.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/product")
public class ProductController {

    @Autowired
    private ProductService productService;

    @GetMapping("/{productId}")
    @SentinelResource(value = "productDetail", blockHandler = "handleProductBlock", fallback = "handleProductFallback")
    public Result<Product> getProductDetail(@PathVariable("productId") Long productId) {
        Product product = productService.getProductDetail(productId);
        if (product != null) {
            return Result.success(product);
        }
        return Result.error("商品未找到");
    }

    public Result<Product> handleProductBlock(Long productId, BlockException ex) {
        return Result.error("当前访问过于频繁，已触发限流: " + ex.getClass().getSimpleName());
    }

    public Result<Product> handleProductFallback(Long productId, Throwable throwable) {
        return Result.error("服务降级中，请稍后重试: " + throwable.getMessage());
    }

    @GetMapping("/seckill/list")
    public Result<?> getSeckillProductList() {
        return Result.success(null);
    }
}
