package com.yarukov.nosql.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private Long id;
    private String username;
    private String fullName;
    private String phone;
    private String deliveryAddress;
    private String role;

    // Поля для демонстрации работы Riak KV преподавателю:
    private boolean fromCache;  // Взято из Riak (true) или из Postgres (false)
    private long visitCount;    // Атомарный счетчик просмотров из Riak
}