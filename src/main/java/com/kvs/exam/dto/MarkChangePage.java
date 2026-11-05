package com.kvs.exam.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/** One page of the marks change history, newest first. */
@Value
@Builder
public class MarkChangePage {
    List<MarkChangeResponse> items;
    int page;
    int size;
    long total;
    int pages;
}
