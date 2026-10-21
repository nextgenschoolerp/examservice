package com.kvs.exam.repo;

import com.kvs.exam.entity.GradingScale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GradingScaleRepository extends JpaRepository<GradingScale, UUID> {

    List<GradingScale> findByTenantIdOrderByScaleNameAsc(String tenantId);

    boolean existsByTenantIdAndScaleNameIgnoreCase(String tenantId, String scaleName);
}
