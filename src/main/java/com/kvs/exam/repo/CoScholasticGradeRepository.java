package com.kvs.exam.repo;

import com.kvs.exam.entity.CoScholasticGrade;
import com.kvs.exam.enums.CoScholasticArea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CoScholasticGradeRepository extends JpaRepository<CoScholasticGrade, UUID> {

    List<CoScholasticGrade> findByExamTermIdAndStudentIdIn(UUID examTermId, Collection<UUID> studentIds);

    Optional<CoScholasticGrade> findByExamTermIdAndStudentIdAndArea(UUID examTermId, UUID studentId, CoScholasticArea area);
}
