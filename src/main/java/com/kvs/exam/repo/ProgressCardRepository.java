package com.kvs.exam.repo;

import com.kvs.exam.entity.ProgressCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProgressCardRepository extends JpaRepository<ProgressCard, UUID> {

    Optional<ProgressCard> findByExamTermIdAndStudentId(UUID examTermId, UUID studentId);

    List<ProgressCard> findByStudentId(UUID studentId);

    List<ProgressCard> findByExamTermIdAndStudentIdIn(UUID examTermId, Collection<UUID> studentIds);
}
