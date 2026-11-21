package com.kvs.exam.repo;

import com.kvs.exam.entity.ExamTermRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExamTermRoomRepository extends JpaRepository<ExamTermRoom, UUID> {

    Optional<ExamTermRoom> findByExamTermIdAndSectionId(UUID examTermId, UUID sectionId);
}
