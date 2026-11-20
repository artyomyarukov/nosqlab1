package com.yarukov.nosql.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketAnalyticsResponse(List<Row> rows) {

    public record Row(String key, Stats value) {}

    public record Stats(double sum, long count, double min, double max, double sumsqr) {}
}
