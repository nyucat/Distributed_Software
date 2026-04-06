package com.seckill.user.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserVO {
    private Long userId;
    private String username;
    private String phone;
    private LocalDateTime createTime;
}
