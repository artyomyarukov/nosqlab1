package com.yarukov.nosql.model.riak;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserSession {
    private String token;
    private String username;
    private String operatorName;
    private Instant createdAt;
    private Instant expiresAt;

    @JsonIgnore
    public boolean isExpired() {
        return Instant.now().isAfter(this.expiresAt);
    }
}