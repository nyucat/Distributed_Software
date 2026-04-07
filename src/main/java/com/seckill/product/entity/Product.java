package com.seckill.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("sys_product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long productId;
    
    private String productName;
    
    private BigDecimal price;
    
    private Integer stock;
    
    private Integer status;
    
    private String picUrl;
}