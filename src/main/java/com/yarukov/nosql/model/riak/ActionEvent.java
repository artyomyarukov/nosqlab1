package com.yarukov.nosql.model.riak;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionEvent {
    private Instant timestamp;
    private String action;
    private String operator;
    private String details;
}