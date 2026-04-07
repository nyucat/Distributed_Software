package com.seckill.product.controller;

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

    /**
     * 获取商品详情
     */
    @GetMapping("/{productId}")
    public Result<Product> getProductDetail(@PathVariable("productId") Long productId) {
        Product product = productService.getProductDetail(productId);
        if (product != null) {
            return Result.success(product);
        }
        return Result.error("商品未找到");
    }

    /**
     * 获取秒杀商品列表
     */
    @GetMapping("/seckill/list")
    public Result<?> getSeckillProductList() {
        // 实际项目中应该从数据库查询秒杀商品列表
        return Result.success(null);
    }
}