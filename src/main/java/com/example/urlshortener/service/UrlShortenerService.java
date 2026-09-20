package com.example.urlshortener.service;

import com.example.urlshortener.dto.*;
import com.example.urlshortener.entity.UrlMapping;
import com.example.urlshortener.exception.*;
import com.example.urlshortener.repository.UrlMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlShortenerService {

    private final UrlMappingRepository repository;
    private final Base62Encoder base62Encoder;

    @Transactional
    public CreateUrlResponse create(CreateUrlRequest request, String baseUrl) {
        try {
            log.debug(
                    "Creating URL longUrl={} expiresAt={}",
                    request.getLongUrl(),
                    request.getExpiresAt()
            );

            validateUrl(request.getLongUrl());

            if (request.getExpiresAt() != null && !request.getExpiresAt().isAfter(Instant.now())) {
                throw new BadRequestException("expiresAt must be in the future");
            }

            UrlMapping mapping = UrlMapping.builder()
                    .shortCode("PENDING")
                    .longUrl(request.getLongUrl().trim())
                    .createdAt(Instant.now())
                    .expiresAt(request.getExpiresAt())
                    .clickCount(0)
                    .disabled(false)
                    .build();

            // Flush first so H2 generates the ID.
            mapping = repository.saveAndFlush(mapping);

            String shortCode = base62Encoder.encode(mapping.getId());
            mapping.setShortCode(shortCode);
            mapping = repository.save(mapping);

            log.info(
                    "URL created successfully shortCode={} longUrl={}",
                    shortCode,
                    mapping.getLongUrl()
            );

            return CreateUrlResponse.builder()
                    .shortCode(shortCode)
                    .shortUrl(baseUrl + "/" + shortCode)
                    .longUrl(mapping.getLongUrl())
                    .createdAt(mapping.getCreatedAt())
                    .expiresAt(mapping.getExpiresAt())
                    .build();

        } catch (Exception ex) {
            log.error(
                    "Error creating URL longUrl={} expiresAt={}",
                    request.getLongUrl(),
                    request.getExpiresAt(),
                    ex
            );
            throw ex;
        }
    }

    @Transactional
    public UrlMapping resolve(String shortCode) {
        try {
            log.debug("Resolving shortCode={}", shortCode);

            UrlMapping mapping = repository.findByShortCode(shortCode)
                    .orElseThrow(() ->
                            new NotFoundException("Short URL not found: " + shortCode));

            if (mapping.isDisabled()) {
                throw new UrlDisabledException(
                        "Short URL is disabled: " + shortCode);
            }

            if (mapping.getExpiresAt() != null &&
                    !mapping.getExpiresAt().isAfter(Instant.now())) {
                throw new UrlExpiredException(
                        "Short URL has expired: " + shortCode);
            }

            mapping.setClickCount(mapping.getClickCount() + 1);
            repository.save(mapping);

            log.info(
                    "URL resolved successfully shortCode={} clickCount={}",
                    shortCode,
                    mapping.getClickCount()
            );

            return mapping;

        } catch (Exception ex) {
            log.error(
                    "Error resolving shortCode={}",
                    shortCode,
                    ex
            );
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse analytics(String shortCode) {
        try {
            log.debug("Fetching analytics shortCode={}", shortCode);

            UrlMapping mapping = repository.findByShortCode(shortCode)
                    .orElseThrow(() ->
                            new NotFoundException("Short URL not found: " + shortCode));

            boolean expired = mapping.getExpiresAt() != null &&
                    !mapping.getExpiresAt().isAfter(Instant.now());

            AnalyticsResponse response = AnalyticsResponse.builder()
                    .shortCode(mapping.getShortCode())
                    .longUrl(mapping.getLongUrl())
                    .createdAt(mapping.getCreatedAt())
                    .expiresAt(mapping.getExpiresAt())
                    .clickCount(mapping.getClickCount())
                    .disabled(mapping.isDisabled())
                    .expired(expired)
                    .build();

            log.info(
                    "Analytics retrieved successfully shortCode={} clickCount={}",
                    shortCode,
                    response.getClickCount()
            );

            return response;

        } catch (Exception ex) {
            log.error(
                    "Error retrieving analytics shortCode={}",
                    shortCode,
                    ex
            );
            throw ex;
        }
    }

    @Transactional
    public void delete(String shortCode) {
        try {
            log.debug("Deleting shortCode={}", shortCode);

            UrlMapping mapping = repository.findByShortCode(shortCode)
                    .orElseThrow(() ->
                            new NotFoundException("Short URL not found: " + shortCode));

            repository.delete(mapping);

            log.info(
                    "URL deleted successfully shortCode={}",
                    shortCode
            );

        } catch (Exception ex) {
            log.error(
                    "Error deleting shortCode={}",
                    shortCode,
                    ex
            );
            throw ex;
        }
    }

    private void validateUrl(String value) {
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();

            if (scheme == null ||
                    (!scheme.equalsIgnoreCase("http") &&
                     !scheme.equalsIgnoreCase("https")) ||
                    uri.getHost() == null) {

                throw new BadRequestException(
                        "longUrl must be a valid HTTP or HTTPS URL");
            }

        } catch (IllegalArgumentException ex) {
            log.error(
                    "Error validating URL value={}",
                    value,
                    ex
            );
            throw new BadRequestException(
                    "longUrl must be a valid HTTP or HTTPS URL");
        }
    }
}
