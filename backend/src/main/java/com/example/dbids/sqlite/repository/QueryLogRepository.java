package com.example.dbids.sqlite.repository;

import com.example.dbids.sqlite.model.QueryLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface QueryLogRepository extends JpaRepository<QueryLog, String>, JpaSpecificationExecutor<QueryLog> {
    @Query("select q from QueryLog q order by q.executedAt desc")
    List<QueryLog> findRecent(Pageable pageable);
}
