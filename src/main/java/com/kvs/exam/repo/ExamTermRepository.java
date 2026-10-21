package com.kvs.exam.repo;

import com.kvs.exam.entity.ExamTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExamTermRepository extends JpaRepository<ExamTerm, UUID> {

    List<ExamTerm> findByTenantIdOrderByStartDateAsc(String tenantId);

    List<ExamTerm> findByTenantIdAndAcademicYearIdOrderByStartDateAsc(String tenantId, UUID academicYearId);

    boolean existsByTenantIdAndAcademicYearIdAndTermNameIgnoreCase(String tenantId, UUID academicYearId, String termName);
}
