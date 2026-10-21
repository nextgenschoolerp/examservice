package com.kvs.exam.repo;

import com.kvs.exam.entity.MarkEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface MarkEntryRepository extends JpaRepository<MarkEntry, UUID> {

    List<MarkEntry> findByExamSubjectIdIn(Collection<UUID> examSubjectIds);

    List<MarkEntry> findByStudentIdOrderByEnteredAtAsc(UUID studentId);

    List<MarkEntry> findByExamTermId(UUID examTermId);
}
