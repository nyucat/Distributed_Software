package com.seckill.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seckill.product.entity.Product;

public interface ProductService extends IService<Product> {
    
    /**
     * 获取商品详情 (带高并发缓存处理)
     */
    Product getProductDetail(Long productId);

    /**
     * 扣减库存 (测试主库写)
     */
    boolean deductStock(Long productId, Integer quantity);
}