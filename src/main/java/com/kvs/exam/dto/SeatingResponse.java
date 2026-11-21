package com.kvs.exam.dto;

import java.util.List;
import java.util.UUID;

/**
 * A class's seating for one exam: its room (null when not set) and each student with the seat printed on the admit card.
 * {@code seat} is the seat set by hand, or the roll number when none is set; {@code seatSet} says which.
 */
public record SeatingResponse(String room, List<Row> students) {

    public record Row(UUID studentId, String studentUid, String name, Integer rollNumber, String seat, boolean seatSet) { }
}
