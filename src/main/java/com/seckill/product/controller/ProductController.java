package com.seckill.product.controller;

import com.seckill.product.entity.Product;
import com.seckill.product.service.ProductService;
import com.seckill.user.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/product")
public class ProductController {

    @Autowired
    private ProductService productService;

    @Value("${server.port}")
    private String serverPort;

    @GetMapping("/{id}")
    public ResponseEntity<Result<Product>> getProductDetail(@PathVariable("id") Long id) {
        try {
            Product product = productService.getProductDetail(id);
            // 往响应头中添加当前处理请求的端口号，方便前端通过 Nginx 验证负载均衡
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Served-By", "Server-Port: " + serverPort);

            if (product != null) {
                return ResponseEntity.ok().headers(headers).body(Result.success(product));
            } else {
                return ResponseEntity.ok().headers(headers).body(Result.error("商品不存在"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Result.error(e.getMessage()));
        }
    }

    @PostMapping("/deduct")
    public Result<String> deductStock(@RequestParam("id") Long id, @RequestParam("quantity") Integer quantity) {
        boolean success = productService.deductStock(id, quantity);
        if (success) {
            return Result.success("库存扣减成功 (写库已路由至 Master)");
        } else {
            return Result.error("库存不足或扣减失败");
        }
    }
}
