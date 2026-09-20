package com.example.urlshortener.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsResponse {
    private String shortCode;
    private String longUrl;
    private Instant createdAt;
    private Instant expiresAt;
    private long clickCount;
    private boolean disabled;
    private boolean expired;
}
