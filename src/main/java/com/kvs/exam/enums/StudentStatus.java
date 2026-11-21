package com.kvs.exam.enums;

public enum StudentStatus {
    /** Saved as a draft in Student records before admission is complete. Not on the roll (ExamAccess), but must load without error. */
    DRAFT,
    ACTIVE,
    PROMOTED,
    GRADUATED,
    TRANSFERRED,
    SUSPENDED
}
