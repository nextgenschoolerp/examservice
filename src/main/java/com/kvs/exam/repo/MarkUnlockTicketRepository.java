package com.kvs.exam.repo;

import com.kvs.exam.entity.MarkUnlockTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MarkUnlockTicketRepository extends JpaRepository<MarkUnlockTicket, UUID> {

    List<MarkUnlockTicket> findByExamTermIdOrderByCreatedAtDesc(UUID examTermId);

    List<MarkUnlockTicket> findByExamSubjectIdAndSectionId(UUID examSubjectId, UUID sectionId);
}
