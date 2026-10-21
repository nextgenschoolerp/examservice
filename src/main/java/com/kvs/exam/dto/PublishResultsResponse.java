package com.kvs.exam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Result of publishing progress cards. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishResultsResponse {

    private UUID examTermId;
    private String termName;
    private UUID sectionId;
    private String className;
    private int published;
    private int alreadyPublished;
    private Instant publishedAt;
}
