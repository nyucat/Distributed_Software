package com.seckill.order.raft;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RaftLogEntry {
    private Long index;
    private Long term;
    private String cmd;
    private LocalDateTime createTime;
}
