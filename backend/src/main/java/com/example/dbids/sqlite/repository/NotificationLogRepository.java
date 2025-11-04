package com.example.dbids.sqlite.repository;

import com.example.dbids.sqlite.model.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, String> {}
