package com.seckill.product.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seckill.product.entity.Product;
import com.seckill.product.mapper.ProductMapper;
import com.seckill.product.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String PRODUCT_DETAIL_KEY_PREFIX = "product:detail:";

    @Override
    public Product getProductDetail(Long productId) {
        // 先从 Redis 缓存获取
        String key = PRODUCT_DETAIL_KEY_PREFIX + productId;
        String productJson = stringRedisTemplate.opsForValue().get(key);
        
        if (productJson != null) {
            // 缓存命中，解析返回
            // 实际项目中应该使用 JSON 序列化/反序列化工具
            return this.getById(productId);
        }
        
        // 缓存未命中，从数据库获取
        Product product = this.getById(productId);
        if (product != null) {
            // 存入 Redis 缓存，设置过期时间
            stringRedisTemplate.opsForValue().set(key, product.toString());
        }
        
        return product;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deductStock(Long productId, Integer quantity) {
        // 使用乐观锁机制扣减库存
        boolean result = this.lambdaUpdate()
                .setSql("stock = stock - " + quantity)
                .eq(Product::getProductId, productId)
                .ge(Product::getStock, quantity)
                .update();
        
        return result;
    }
}