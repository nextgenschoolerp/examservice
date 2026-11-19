package com.kvs.exam.dto;

import java.util.List;
import java.util.UUID;

/** Seats set by hand for students of one exam; a blank seat takes the student back to the roll number. */
public record SaveSeatingRequest(List<Entry> entries) {

    public record Entry(UUID studentId, String seat) { }
}
