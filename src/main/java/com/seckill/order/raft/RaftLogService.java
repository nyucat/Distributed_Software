package com.seckill.order.raft;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RaftLogService {

    private static final String LOG_INDEX_KEY = "raft:order:log:index";
    private static final String LOG_LIST_KEY = "raft:order:log:list";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 以追加模式写入 Raft 日志，不允许修改历史日志。
     */
    public RaftLogEntry appendLog(Long term, String cmd) {
        Long index = stringRedisTemplate.opsForValue().increment(LOG_INDEX_KEY);
        if (index == null) {
            throw new IllegalStateException("Raft 日志索引生成失败");
        }

        RaftLogEntry entry = new RaftLogEntry(index, term, cmd, LocalDateTime.now());
        String entryJson = JSONUtil.toJsonStr(entry);
        stringRedisTemplate.opsForList().rightPush(LOG_LIST_KEY, entryJson);
        log.info("Raft log appended. term={}, index={}, cmd={}", term, index, cmd);
        return entry;
    }

    /**
     * 读取最近 N 条日志，便于调试观测。
     */
    public List<RaftLogEntry> queryLastLogs(int count) {
        if (count <= 0) {
            return Collections.emptyList();
        }
        Long size = stringRedisTemplate.opsForList().size(LOG_LIST_KEY);
        if (size == null || size <= 0) {
            return Collections.emptyList();
        }
        long start = Math.max(0, size - count);
        long end = size - 1;
        List<String> rawList = stringRedisTemplate.opsForList().range(LOG_LIST_KEY, start, end);
        if (rawList == null || rawList.isEmpty()) {
            return Collections.emptyList();
        }
        return rawList.stream()
                .map(item -> JSONUtil.toBean(item, RaftLogEntry.class))
                .collect(Collectors.toList());
    }
}
