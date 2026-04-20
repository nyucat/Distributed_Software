package com.seckill.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class GatewayFallbackController {

    @GetMapping("/seckill")
    public ResponseEntity<Map<String, Object>> seckillFallback() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", 503);
        payload.put("message", "Gateway circuit breaker fallback");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(payload);
    }
}
