package com.kvs.exam.repo;

import com.kvs.exam.entity.ExamSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExamSubjectRepository extends JpaRepository<ExamSubject, UUID> {

    List<ExamSubject> findByExamTermIdAndGradeIdOrderByDisplayOrderAsc(UUID examTermId, UUID gradeId);

    List<ExamSubject> findByExamTermIdOrderByDisplayOrderAsc(UUID examTermId);

    Optional<ExamSubject> findByExamTermIdAndGradeIdAndSubjectCode(UUID examTermId, UUID gradeId, String subjectCode);
}
