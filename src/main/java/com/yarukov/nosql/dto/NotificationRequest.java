package com.yarukov.nosql.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NotificationRequest {
    @NotBlank(message = "Текст уведомления не может быть пустым")
    private String message;
    private String type;
}