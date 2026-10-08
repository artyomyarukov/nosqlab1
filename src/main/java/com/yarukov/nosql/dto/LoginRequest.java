package com.yarukov.nosql.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
public class LoginRequest {
    @NotBlank(message = "Username оператора обязателен")
    private String username;
}