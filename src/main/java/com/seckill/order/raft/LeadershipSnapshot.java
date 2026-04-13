package com.seckill.order.raft;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LeadershipSnapshot {
    private final boolean raftEnabled;
    private final boolean leader;
    private final String nodeId;
    private final String currentLeaderId;
    private final Long term;

    public static LeadershipSnapshot disabled(String nodeId) {
        return new LeadershipSnapshot(false, true, nodeId, nodeId, 0L);
    }

    public static LeadershipSnapshot leader(String nodeId, Long term) {
        return new LeadershipSnapshot(true, true, nodeId, nodeId, term);
    }

    public static LeadershipSnapshot follower(String nodeId, String currentLeaderId, Long term) {
        return new LeadershipSnapshot(true, false, nodeId, currentLeaderId, term);
    }
}
