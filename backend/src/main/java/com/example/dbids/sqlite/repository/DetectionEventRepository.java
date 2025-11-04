package com.example.dbids.sqlite.repository;

import com.example.dbids.sqlite.model.DetectionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetectionEventRepository extends JpaRepository<DetectionEvent, String> {
}
