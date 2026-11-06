package com.yarukov.nosql.model.couchdb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentMeta {

    private String fileName;
    private String contentType;
    private Long fileSize;
    private Instant uploadedAt;
}