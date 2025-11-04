package com.example.dbids.modules.behavior;

import com.example.dbids.modules.rule.SqlNormalizer;
import com.example.dbids.sqlite.model.QueryLog;

public class BehaviorFeatureExtractor {

    private final SqlNormalizer normalizer = new SqlNormalizer();
    private final QueryClassifier classifier = new QueryClassifier();

    public static class Delta {
        public final int q;              // 쿼리 개수(항상 1)
        public final int write;          // INSERT/UPDATE/DELETE면 1
        public final int ddl;            // DDL이면 1
        public final int error;          // 실패이면 1
        public final int distinctTables; // 추정된 테이블 수
        Delta(int q, int write, int ddl, int error, int distinctTables) {
            this.q = q; this.write = write; this.ddl = ddl; this.error = error; this.distinctTables = distinctTables;
        }
    }

    public static boolean isWrite(QueryClassifier.Kind k) {
        return k == QueryClassifier.Kind.INSERT || k == QueryClassifier.Kind.UPDATE || k == QueryClassifier.Kind.DELETE;
    }

    public Delta extract(QueryLog log) {
        String norm = normalizer.normalize(log.getSqlRaw());
        QueryClassifier.Kind kind = classifier.classify(norm);
        boolean write = isWrite(kind);
        boolean ddl   = (kind == QueryClassifier.Kind.DDL);
        boolean err   = (log.getStatus() == QueryLog.Status.FAILURE);
        int tables    = classifier.guessTables(norm).length;
        return new Delta(1, write ? 1 : 0, ddl ? 1 : 0, err ? 1 : 0, tables);
    }
}
