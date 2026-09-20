package com.example.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUrlRequest {

    @NotBlank(message = "longUrl must not be blank")
    @Size(max = 2048, message = "longUrl must not exceed 2048 characters")
    private String longUrl;

    private Instant expiresAt;
}
