package com.api.codenest;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeSummaryRepository extends JpaRepository<CodeSummaryEntity, String> {
}