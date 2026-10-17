package com.yarukov.nosql.repository.jpa;

import com.yarukov.nosql.model.entity.History;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistoryRepository extends JpaRepository<History, Long> {
    List<History> findByUserIdOrderByTimestampDesc(long userId);
}