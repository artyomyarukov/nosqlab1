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
public class CachedUserProfile {
    private Long id;
    private String username;
    private String fullName;
    private String phone;
    private String deliveryAddress;
    private String role;
    private Instant cachedAt;
}