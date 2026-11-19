package com.kvs.exam.service;

import com.kvs.core.exception.BadRequestException;
import com.kvs.exam.dto.SaveSeatingRequest;
import com.kvs.exam.dto.SeatingResponse;
import com.kvs.exam.entity.ExamSeat;
import com.kvs.exam.entity.ExamTerm;
import com.kvs.exam.entity.ExamTermRoom;
import com.kvs.exam.entity.Section;
import com.kvs.exam.entity.StudentMaster;
import com.kvs.exam.repo.ExamSeatRepository;
import com.kvs.exam.repo.ExamTermRoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Where each class sits an exam (Exam & Assessment → seating): the room of the class, and each student's seat. The seat
 * is set by hand, or it is the roll number when none is set. Both print on the admit cards.
 */
@Service
public class SeatingService {

    static final int MAX_ROOM = 60;
    static final int MAX_SEAT = 20;

    @Autowired private ExamAccess access;
    @Autowired private ExamTermRoomRepository rooms;
    @Autowired private ExamSeatRepository seats;

    /** The room of a class in a term, or null when none has been set */
    public String roomOf(UUID examTermId, UUID sectionId) {
        return rooms.findByExamTermIdAndSectionId(examTermId, sectionId).map(ExamTermRoom::getRoomName).orElse(null);
    }

    /** The seats set by hand in a term, by student; students not listed sit by roll number */
    public Map<UUID, String> seatsOf(UUID examTermId) {
        return seats.findByExamTermId(examTermId).stream()
                .collect(Collectors.toMap(ExamSeat::getStudentId, ExamSeat::getSeatNumber, (a, b) -> b));
    }

    /** The seat printed for a student: the seat set by hand, else the roll number, else null */
    public static String effectiveSeat(String seatSetByHand, Integer rollNumber) {
        if (seatSetByHand != null && !seatSetByHand.isBlank()) return seatSetByHand;
        return rollNumber == null ? null : String.valueOf(rollNumber);
    }

    /** The seating of a class: its room and each student of the class by roll number */
    public SeatingResponse seating(UUID examTermId, UUID sectionId, String branchId) {
        ExamTerm term = access.term(examTermId);
        Section section = access.section(sectionId, term.getTenantId(), ExamAccess.VIEW_TERMS);
        String campus = access.campus(ExamAccess.VIEW_TERMS, branchId);
        Map<UUID, String> byHand = seatsOf(examTermId);
        List<SeatingResponse.Row> rows = access.roster(section, term, campus).stream()
                .sorted(Comparator.comparing(StudentMaster::getRollNumber, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(s -> new SeatingResponse.Row(s.getStudentId(), s.getStudentUid(), name(s), s.getRollNumber(),
                        effectiveSeat(byHand.get(s.getStudentId()), s.getRollNumber()), byHand.containsKey(s.getStudentId())))
                .toList();
        return new SeatingResponse(roomOf(examTermId, sectionId), rows);
    }

    /** Sets the room of a class in a term; a blank room clears it. Returns the room now set (null when cleared). */
    @Transactional
    public String saveRoom(UUID examTermId, UUID sectionId, String room) {
        ExamTerm term = access.term(examTermId);
        access.section(sectionId, term.getTenantId());
        String name = room == null ? "" : room.trim();
        if (name.length() > MAX_ROOM) {
            throw new BadRequestException("The room must be " + MAX_ROOM + " characters or fewer");
        }
        Optional<ExamTermRoom> existing = rooms.findByExamTermIdAndSectionId(examTermId, sectionId);
        if (name.isEmpty()) {
            existing.ifPresent(rooms::delete);
            return null;
        }
        ExamTermRoom row = existing.orElseGet(ExamTermRoom::new);
        row.setExamTermId(examTermId);
        row.setSectionId(sectionId);
        row.setRoomName(name);
        rooms.save(row);
        return name;
    }

    /** Sets seats by hand for students of a term; a blank seat takes the student back to the roll number. */
    @Transactional
    public void saveSeats(UUID examTermId, SaveSeatingRequest request) {
        ExamTerm term = access.term(examTermId);
        if (request == null || request.entries() == null || request.entries().isEmpty()) {
            throw new BadRequestException("No seats to save");
        }
        Set<UUID> listed = new HashSet<>();
        for (SaveSeatingRequest.Entry e : request.entries()) {
            if (e.studentId() == null) throw new BadRequestException("A seat has no student");
            if (!listed.add(e.studentId())) throw new BadRequestException("A student is listed twice");
            String seat = e.seat() == null ? "" : e.seat().trim();
            if (seat.length() > MAX_SEAT) throw new BadRequestException("A seat must be " + MAX_SEAT + " characters or fewer");
            StudentMaster student = access.student(e.studentId());
            if (!term.getTenantId().equals(student.getTenantId())) {
                throw new BadRequestException("The student belongs to another school");
            }
            Optional<ExamSeat> existing = seats.findByExamTermIdAndStudentId(examTermId, e.studentId());
            if (seat.isEmpty()) {
                existing.ifPresent(seats::delete);
                continue;
            }
            ExamSeat row = existing.orElseGet(ExamSeat::new);
            row.setExamTermId(examTermId);
            row.setStudentId(e.studentId());
            row.setSeatNumber(seat);
            seats.save(row);
        }
    }

    private static String name(StudentMaster s) {
        return (s.getFirstName() + " " + (s.getLastName() == null ? "" : s.getLastName())).trim();
    }
}
