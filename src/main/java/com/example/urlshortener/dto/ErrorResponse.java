package com.example.urlshortener.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private String error;
    private Instant timestamp;
    private String message;
    private int status;
    private String path;
}
