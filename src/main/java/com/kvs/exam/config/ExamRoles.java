package com.kvs.exam.config;

/**
 * Who may call the Module 05 assessment endpoints (@PreAuthorize expressions). BRD actors → roles:
 * Exam Controller → EXAM_CONTROLLER, Subject Teacher and Evaluator → SUBJECT_TEACHER, Class Teacher → CLASS_TEACHER,
 * Academic Principal → SCHOOL_PRINCIPAL, Academic Auditor → ACADEMIC_AUDITOR, Parent → APPLICANT_PARENT (own children,
 * published results only). Students have no login yet.
 */
public final class ExamRoles {

    private ExamRoles() {
    }

    private static final String TEACHING = "'EXAM_CONTROLLER', 'SUBJECT_TEACHER', 'CLASS_TEACHER', 'SCHOOL_PRINCIPAL', "
            + "'VICE_PRINCIPAL', 'ACADEMIC_AUDITOR', 'SUPER_ADMIN'";

    /** US-05000: grading scales, terms and subjects; US-05005: lock a term */
    public static final String CONFIGURE = "hasAnyRole('EXAM_CONTROLLER', 'SCHOOL_PRINCIPAL', 'SUPER_ADMIN')";

    /** Read terms, subjects, scales, broadsheets, rank lists and completeness */
    public static final String VIEW = "hasAnyRole(" + TEACHING + ", 'REGISTRAR')";

    /** School-wide results of a term (Academics outlook): school leadership and the exam controller, not individual teachers */
    public static final String SCHOOL_RESULTS = "hasAnyRole('SCHOOL_PRINCIPAL', 'VICE_PRINCIPAL', 'EXAM_CONTROLLER', 'ACADEMIC_AUDITOR', 'SUPER_ADMIN')";

    /** US-05003: web mark entry */
    public static final String ENTER_MARKS = "hasAnyRole('SUBJECT_TEACHER', 'CLASS_TEACHER', 'EXAM_CONTROLLER', 'SUPER_ADMIN')";

    /** US-05006: ask to reopen locked marks */
    public static final String REQUEST_UNLOCK = "hasAnyRole('SUBJECT_TEACHER', 'CLASS_TEACHER', 'SUPER_ADMIN')";

    /** US-05006: approve or reject an unlock ticket */
    public static final String DECIDE_UNLOCK = "hasAnyRole('SCHOOL_PRINCIPAL', 'EXAM_CONTROLLER', 'SUPER_ADMIN')";

    /** US-05008: co-scholastic grades */
    public static final String CO_SCHOLASTIC = "hasAnyRole('CLASS_TEACHER', 'SUBJECT_TEACHER', 'EXAM_CONTROLLER', 'SUPER_ADMIN')";

    /** US-05018: teacher remarks on the progress card */
    public static final String REMARKS = "hasAnyRole('CLASS_TEACHER', 'SCHOOL_PRINCIPAL', 'SUPER_ADMIN')";

    /** US-05010: publish progress cards */
    public static final String PUBLISH = "hasAnyRole('CLASS_TEACHER', 'SCHOOL_PRINCIPAL', 'EXAM_CONTROLLER', 'SUPER_ADMIN')";

    /** US-05009, US-05013: a student's progress card and progress trend (student profile) */
    public static final String STUDENT_PROGRESS = "hasAnyRole(" + TEACHING + ", 'REGISTRAR', 'APPLICANT_PARENT')";
}
