// src/main/java/com/example/dbids/dto/LogSummaryResponse.java
package com.example.dbids.dto;

import java.util.List;

public class LogSummaryResponse {
    public List<UserBucket> users;   // 사용자별
    public List<TimeBucket> times;   // 시간대별(시간 단위)

    public LogSummaryResponse(List<UserBucket> users, List<TimeBucket> times) {
        this.users = users; this.times = times;
    }

    public static class UserBucket {
        public String userId;
        public long total;
        public long success;
        public long failure;
        public long rowsSum;
        public UserBucket(String userId, long total, long success, long failure, long rowsSum) {
            this.userId = userId; this.total = total; this.success = success;
            this.failure = failure; this.rowsSum = rowsSum;
        }
    }

    public static class TimeBucket {
        /** 예: 2025-10-27T15:00 (로컬/UTC 표기는 저장 포맷 그대로) */
        public String hour;
        public long total;
        public long success;
        public long failure;
        public long rowsSum;
        public TimeBucket(String hour, long total, long success, long failure, long rowsSum) {
            this.hour = hour; this.total = total; this.success = success;
            this.failure = failure; this.rowsSum = rowsSum;
        }
    }
}
