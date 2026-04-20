package com.seckill.user.controller;

import com.seckill.user.vo.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RefreshScope
@RestController
@RequestMapping("/api/config")
public class GovernanceConfigController {

    @Value("${governance.message:governance-default}")
    private String governanceMessage;

    @GetMapping("/message")
    public Result<Map<String, String>> currentMessage() {
        Map<String, String> payload = new HashMap<>();
        payload.put("governance.message", governanceMessage);
        return Result.success(payload);
    }
}
