/**
 * examservice entities. Module 05 owns exam_terms, exam_term_subjects, grading_scales, marks_entry_register,
 * grade_lock_tickets, co_scholastic_grades and holistic_progress_cards. {@code StudentMaster}, {@code Grade},
 * {@code Section} and {@code AcademicSession} map studentservice's tables in the shared database as a read model:
 * examservice only reads them (rosters, class names, academic years) and never writes them.
 */
package com.kvs.exam.entity;
