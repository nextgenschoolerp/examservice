package com.kvs.exam.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** The room where one class sits one exam (Exam & Assessment → seating). One row per term and section. */
@Entity
@Table(name = "exam_term_rooms", uniqueConstraints = @UniqueConstraint(name = "uk_exam_term_room", columnNames = {"exam_term_id", "section_id"}))
@Data
@NoArgsConstructor
public class ExamTermRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "exam_term_room_id")
    private UUID examTermRoomId;

    @Column(name = "exam_term_id", nullable = false)
    private UUID examTermId;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "room_name", nullable = false, length = 60)
    private String roomName;
}
