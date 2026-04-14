package com.seckill.product.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seckill.product.entity.Product;
import com.seckill.product.mapper.ProductMapper;
import com.seckill.product.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Slf4j
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String PRODUCT_DETAIL_KEY_PREFIX = "product:detail:";
    private static final String PRODUCT_LOCK_KEY_PREFIX = "product:lock:";
    private static final String NULL_PRODUCT_PLACEHOLDER = "NULL";
    private static final Duration PRODUCT_CACHE_TTL = Duration.ofMinutes(10);
    private static final Duration NULL_CACHE_TTL = Duration.ofMinutes(1);
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);

    @Override
    public Product getProductDetail(Long productId) {
        String cacheKey = PRODUCT_DETAIL_KEY_PREFIX + productId;
        String cacheValue = stringRedisTemplate.opsForValue().get(cacheKey);

        // 命中空值缓存，快速返回避免缓存穿透
        if (NULL_PRODUCT_PLACEHOLDER.equals(cacheValue)) {
            return null;
        }

        // 命中正常缓存，直接反序列化返回
        if (cacheValue != null && !cacheValue.isEmpty()) {
            return JSONUtil.toBean(cacheValue, Product.class);
        }

        String lockKey = PRODUCT_LOCK_KEY_PREFIX + productId;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (Boolean.TRUE.equals(locked)) {
            try {
                Product product = this.getById(productId);
                if (product == null) {
                    stringRedisTemplate.opsForValue().set(cacheKey, NULL_PRODUCT_PLACEHOLDER, NULL_CACHE_TTL);
                    return null;
                }

                stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(product), PRODUCT_CACHE_TTL);
                return product;
            } finally {
                stringRedisTemplate.delete(lockKey);
            }
        }

        // 简易自旋回读，减少击穿时直接打 DB
        try {
            Thread.sleep(50L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String retryValue = stringRedisTemplate.opsForValue().get(cacheKey);
        if (NULL_PRODUCT_PLACEHOLDER.equals(retryValue)) {
            return null;
        }
        if (retryValue != null && !retryValue.isEmpty()) {
            return JSONUtil.toBean(retryValue, Product.class);
        }

        // 兜底查询（极端并发下仍保证可用）
        return this.getById(productId);
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