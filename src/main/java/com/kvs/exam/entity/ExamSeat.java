package com.kvs.exam.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * A seat number a student has been given by hand for one exam. Students without one sit by their roll number.
 * One row per term and student.
 */
@Entity
@Table(name = "exam_seats", uniqueConstraints = @UniqueConstraint(name = "uk_exam_seat", columnNames = {"exam_term_id", "student_id"}))
@Data
@NoArgsConstructor
public class ExamSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "exam_seat_id")
    private UUID examSeatId;

    @Column(name = "exam_term_id", nullable = false)
    private UUID examTermId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "seat_number", nullable = false, length = 20)
    private String seatNumber;
}
