package com.seckill.product.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seckill.product.entity.Product;
import com.seckill.product.mapper.ProductMapper;
import com.seckill.product.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    private static final String CACHE_KEY_PREFIX = "product:detail:";
    private static final String LOCK_KEY_PREFIX = "lock:product:detail:";
    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    private static final String CACHE_NULL_VALUE = "null";

    @PostConstruct
    public void initStock() {
        // 系统启动时，预热商品库存到 Redis 中，方便秒杀高并发预扣减
        Product product = this.getById(1L);
        if (product != null) {
            stringRedisTemplate.opsForValue().set(STOCK_KEY_PREFIX + product.getProductId(), String.valueOf(product.getStock()));
            log.info("预热商品库存成功: productId={}, stock={}", product.getProductId(), product.getStock());
        }
    }

    @Override
    public Product getProductDetail(Long productId) {
        String cacheKey = CACHE_KEY_PREFIX + productId;

        // 1. 查询缓存
        String productJson = stringRedisTemplate.opsForValue().get(cacheKey);

        // 命中缓存直接返回
        if (StrUtil.isNotBlank(productJson)) {
            // 【缓存穿透处理】如果是之前为了防穿透写入的空值，直接返回空
            if (CACHE_NULL_VALUE.equals(productJson)) {
                return null;
            }
            return JSONUtil.toBean(productJson, Product.class);
        }

        // 如果缓存命中但字符串为空白，防范脏数据
        if (productJson != null && productJson.trim().isEmpty()) {
            return null;
        }

        // 2. 缓存中没有，需要查询数据库。使用 Redisson 分布式锁防【缓存击穿】
        String lockKey = LOCK_KEY_PREFIX + productId;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 尝试获取锁，最多等待 2 秒，锁的过期时间为 10 秒
            boolean isLocked = lock.tryLock(2, 10, TimeUnit.SECONDS);
            if (isLocked) {
                // 获取到锁后，再次检查缓存 (Double Check)，防止其他线程已经加载到缓存
                productJson = stringRedisTemplate.opsForValue().get(cacheKey);
                if (StrUtil.isNotBlank(productJson)) {
                    if (CACHE_NULL_VALUE.equals(productJson)) {
                        return null;
                    }
                    return JSONUtil.toBean(productJson, Product.class);
                }

                // 3. 查数据库
                Product product = this.getById(productId);

                // 4. 【缓存穿透处理】数据库查不到，写一个空值到缓存中，并设置较短过期时间
                if (product == null) {
                    stringRedisTemplate.opsForValue().set(cacheKey, CACHE_NULL_VALUE, 2, TimeUnit.MINUTES);
                    return null;
                }

                // 5. 【缓存雪崩处理】正常商品写入缓存，设置基础过期时间 + 随机过期时间，防止大量 key 同时失效
                // 基础时间 1 小时，加上 1~10 分钟的随机时间
                long expireTime = 60 * 60 + RandomUtil.randomInt(60, 600);
                stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(product), expireTime, TimeUnit.SECONDS);

                return product;
            } else {
                // 没有获取到锁，说明有其他线程正在查库和重建缓存，休眠一会再重试
                Thread.sleep(100);
                return getProductDetail(productId); // 递归重试
            }
        } catch (InterruptedException e) {
            log.error("获取商品详情时分布式锁异常", e);
            throw new RuntimeException("获取商品详情异常");
        } finally {
            // 释放锁（只释放自己加的锁）
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    @Override
    public boolean deductStock(Long productId, Integer quantity) {
        // 在实际业务中，由于库存扣减涉及并发，通常应该在数据库通过 `update set stock = stock - N where stock >= N` 保证
        // 或者直接通过 Redis + Lua 实现，这里为了验证读写分离 (主库写)，仅演示通过 MyBatis-Plus 去更新数据
        Product product = this.getById(productId);
        if (product != null && product.getStock() >= quantity) {
            product.setStock(product.getStock() - quantity);
            boolean updated = this.updateById(product);
            if (updated) {
                // 清理缓存以保证一致性
                stringRedisTemplate.delete(CACHE_KEY_PREFIX + productId);
            }
            return updated;
        }
        return false;
    }
}
