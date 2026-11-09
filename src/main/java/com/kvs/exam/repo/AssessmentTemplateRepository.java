package com.kvs.exam.repo;

import com.kvs.exam.entity.AssessmentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AssessmentTemplateRepository extends JpaRepository<AssessmentTemplate, UUID> {

    /** The master templates and the school's own copies */
    @Query("select t from AssessmentTemplate t where t.tenantId is null or t.tenantId = :tenantId order by t.boardCode, t.tenantId nulls first, t.templateName")
    List<AssessmentTemplate> findVisible(@Param("tenantId") String tenantId);

    boolean existsByTemplateCode(String templateCode);

    boolean existsByTenantIdAndTemplateNameIgnoreCase(String tenantId, String templateName);
}
