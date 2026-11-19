package com.kvs.exam.repo;

import com.kvs.exam.entity.ExamSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExamSeatRepository extends JpaRepository<ExamSeat, UUID> {

    List<ExamSeat> findByExamTermId(UUID examTermId);

    Optional<ExamSeat> findByExamTermIdAndStudentId(UUID examTermId, UUID studentId);
}
