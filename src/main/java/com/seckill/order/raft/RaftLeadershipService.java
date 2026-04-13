package com.seckill.order.raft;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
public class RaftLeadershipService {

    private static final String LEADER_KEY = "raft:order:leader";
    private static final String TERM_KEY = "raft:order:term";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${raft.optimization.enabled:false}")
    private boolean raftOptimizationEnabled;

    @Value("${raft.optimization.leader-lease-seconds:8}")
    private long leaderLeaseSeconds;

    private final String nodeId = buildNodeId();

    public LeadershipSnapshot tryAcquireOrRenewLeadership() {
        if (!raftOptimizationEnabled) {
            return LeadershipSnapshot.disabled(nodeId);
        }

        String currentLeader = stringRedisTemplate.opsForValue().get(LEADER_KEY);

        // 已是 leader，续租。
        if (nodeId.equals(currentLeader)) {
            stringRedisTemplate.expire(LEADER_KEY, Duration.ofSeconds(leaderLeaseSeconds));
            Long term = readCurrentTerm();
            return LeadershipSnapshot.leader(nodeId, term);
        }

        // 尝试成为 leader。
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(LEADER_KEY, nodeId, Duration.ofSeconds(leaderLeaseSeconds));

        if (Boolean.TRUE.equals(acquired)) {
            Long newTerm = stringRedisTemplate.opsForValue().increment(TERM_KEY);
            if (newTerm == null) {
                newTerm = 1L;
                stringRedisTemplate.opsForValue().set(TERM_KEY, String.valueOf(newTerm));
            }
            log.info("Raft leader elected. nodeId={}, term={}", nodeId, newTerm);
            return LeadershipSnapshot.leader(nodeId, newTerm);
        }

        Long term = readCurrentTerm();
        return LeadershipSnapshot.follower(nodeId, currentLeader, term);
    }

    private Long readCurrentTerm() {
        String val = stringRedisTemplate.opsForValue().get(TERM_KEY);
        if (val == null) {
            return 0L;
        }
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String buildNodeId() {
        String host = "unknown-host";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ignored) {
        }
        return host + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
