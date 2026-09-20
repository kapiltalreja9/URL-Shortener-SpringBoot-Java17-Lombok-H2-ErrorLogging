package com.example.urlshortener.service;

import com.example.urlshortener.dto.AnalyticsResponse;
import com.example.urlshortener.dto.CreateUrlRequest;
import com.example.urlshortener.dto.CreateUrlResponse;
import com.example.urlshortener.entity.UrlMapping;
import com.example.urlshortener.exception.BadRequestException;
import com.example.urlshortener.exception.NotFoundException;
import com.example.urlshortener.exception.UrlDisabledException;
import com.example.urlshortener.exception.UrlExpiredException;
import com.example.urlshortener.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UrlShortenerServiceTest {

    @Mock
    private UrlMappingRepository repository;

    @Mock
    private Base62Encoder base62Encoder;

    @InjectMocks
    private UrlShortenerService service;

    private static final String LONG_URL = "https://example.com/test";
    private static final String SHORT_CODE = "abc123";

    private UrlMapping mapping;

    @BeforeEach
    void setUp() {
        mapping = UrlMapping.builder()
                .id(1L)
                .shortCode(SHORT_CODE)
                .longUrl(LONG_URL)
                .createdAt(Instant.now())
                .expiresAt(null)
                .clickCount(0)
                .disabled(false)
                .build();
    }

    // ---------------------------------------------------------
    // CREATE
    // ---------------------------------------------------------

    @Test
    void shouldCreateUrlSuccessfully() {

        CreateUrlRequest request = CreateUrlRequest.builder()
                .longUrl(LONG_URL)
                .build();

        UrlMapping savedMapping = UrlMapping.builder()
                .id(1L)
                .shortCode("PENDING")
                .longUrl(LONG_URL)
                .createdAt(Instant.now())
                .expiresAt(null)
                .clickCount(0)
                .disabled(false)
                .build();

        when(repository.saveAndFlush(any(UrlMapping.class)))
                .thenReturn(savedMapping);

        when(base62Encoder.encode(1L))
                .thenReturn(SHORT_CODE);

        when(repository.save(any(UrlMapping.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateUrlResponse response =
                service.create(request, "http://localhost:8080");

        assertNotNull(response);
        assertEquals(SHORT_CODE, response.getShortCode());
        assertEquals(
                "http://localhost:8080/" + SHORT_CODE,
                response.getShortUrl()
        );
        assertEquals(LONG_URL, response.getLongUrl());

        verify(repository).saveAndFlush(any(UrlMapping.class));
        verify(repository).save(any(UrlMapping.class));
        verify(base62Encoder).encode(1L);
    }

    @Test
    void shouldTrimLongUrlBeforeSaving() {

        CreateUrlRequest request = CreateUrlRequest.builder()
                .longUrl("  https://example.com/test  ")
                .build();

        when(repository.saveAndFlush(any(UrlMapping.class)))
                .thenAnswer(invocation -> {
                    UrlMapping value = invocation.getArgument(0);
                    value.setId(10L);
                    return value;
                });

        when(base62Encoder.encode(10L))
                .thenReturn("a");

        when(repository.save(any(UrlMapping.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateUrlResponse response =
                service.create(request, "http://localhost:8080");

        assertEquals("https://example.com/test", response.getLongUrl());
    }

    @Test
    void shouldCreateUrlWithExpiration() {

        Instant expiresAt = Instant.now().plusSeconds(3600);

        CreateUrlRequest request = CreateUrlRequest.builder()
                .longUrl(LONG_URL)
                .expiresAt(expiresAt)
                .build();

        when(repository.saveAndFlush(any(UrlMapping.class)))
                .thenAnswer(invocation -> {
                    UrlMapping value = invocation.getArgument(0);
                    value.setId(1L);
                    return value;
                });

        when(base62Encoder.encode(1L))
                .thenReturn(SHORT_CODE);

        when(repository.save(any(UrlMapping.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateUrlResponse response =
                service.create(request, "http://localhost:8080");

        assertEquals(expiresAt, response.getExpiresAt());
    }

    @Test
    void shouldRejectExpiredExpirationTime() {

        CreateUrlRequest request = CreateUrlRequest.builder()
                .longUrl(LONG_URL)
                .expiresAt(Instant.now().minusSeconds(10))
                .build();

        assertThrows(
                BadRequestException.class,
                () -> service.create(request, "http://localhost:8080")
        );

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectCurrentExpirationTime() {

        CreateUrlRequest request = CreateUrlRequest.builder()
                .longUrl(LONG_URL)
                .expiresAt(Instant.now())
                .build();

        assertThrows(
                BadRequestException.class,
                () -> service.create(request, "http://localhost:8080")
        );
    }

    @Test
    void shouldRejectInvalidUrl() {

        CreateUrlRequest request = CreateUrlRequest.builder()
                .longUrl("not-a-url")
                .build();

        BadRequestException exception =
                assertThrows(
                        BadRequestException.class,
                        () -> service.create(request, "http://localhost:8080")
                );

        assertEquals(
                "longUrl must be a valid HTTP or HTTPS URL",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectFtpUrl() {

        CreateUrlRequest request = CreateUrlRequest.builder()
                .longUrl("ftp://example.com/file")
                .build();

        assertThrows(
                BadRequestException.class,
                () -> service.create(request, "http://localhost:8080")
        );
    }

    @Test
    void shouldRejectUrlWithoutHost() {

        CreateUrlRequest request = CreateUrlRequest.builder()
                .longUrl("https:///abc")
                .build();

        assertThrows(
                BadRequestException.class,
                () -> service.create(request, "http://localhost:8080")
        );
    }

    @Test
    void shouldHandleRepositoryExceptionDuringCreate() {

        CreateUrlRequest request = CreateUrlRequest.builder()
                .longUrl(LONG_URL)
                .build();

        when(repository.saveAndFlush(any(UrlMapping.class)))
                .thenThrow(new RuntimeException("Database error"));

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> service.create(request, "http://localhost:8080")
                );

        assertEquals("Database error", exception.getMessage());
    }

    // ---------------------------------------------------------
    // RESOLVE
    // ---------------------------------------------------------

    @Test
    void shouldResolveUrlAndIncrementClickCount() {

        when(repository.findByShortCode(SHORT_CODE))
                .thenReturn(Optional.of(mapping));

        when(repository.save(any(UrlMapping.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UrlMapping result =
                service.resolve(SHORT_CODE);

        assertEquals(SHORT_CODE, result.getShortCode());
        assertEquals(1, result.getClickCount());

        verify(repository).save(mapping);
    }

    @Test
    void shouldIncrementClickCountMultipleTimes() {

        mapping.setClickCount(10);

        when(repository.findByShortCode(SHORT_CODE))
                .thenReturn(Optional.of(mapping));

        service.resolve(SHORT_CODE);

        assertEquals(11, mapping.getClickCount());
    }

    @Test
    void shouldThrowWhenShortCodeDoesNotExist() {

        when(repository.findByShortCode(SHORT_CODE))
                .thenReturn(Optional.empty());

        NotFoundException exception =
                assertThrows(
                        NotFoundException.class,
                        () -> service.resolve(SHORT_CODE)
                );

        assertEquals(
                "Short URL not found: " + SHORT_CODE,
                exception.getMessage()
        );

        verify(repository, never()).save(any());
    }

    @Test
    void shouldThrowWhenUrlDisabled() {

        mapping.setDisabled(true);

        when(repository.findByShortCode(SHORT_CODE))
                .thenReturn(Optional.of(mapping));

        UrlDisabledException exception =
                assertThrows(
                        UrlDisabledException.class,
                        () -> service.resolve(SHORT_CODE)
                );

        assertEquals(
                "Short URL is disabled: " + SHORT_CODE,
                exception.getMessage()
        );

        verify(repository, never()).save(any());
    }

    @Test
    void shouldThrowWhenUrlExpired() {

        mapping.setExpiresAt(
                Instant.now().minusSeconds(60)
        );

        when(repository.findByShortCode(SHORT_CODE))
                .thenReturn(Optional.of(mapping));

        UrlExpiredException exception =
                assertThrows(
                        UrlExpiredException.class,
                        () -> service.resolve(SHORT_CODE)
                );

        assertEquals(
                "Short URL has expired: " + SHORT_CODE,
                exception.getMessage()
        );

        verify(repository, never()).save(any());
    }

    @Test
    void shouldResolveUrlWhenExpirationIsInFuture() {

        mapping.setExpiresAt(
                Instant.now().plusSeconds(3600)
        );

        when(repository.findByShortCode(SHORT_CODE))
                .thenReturn(Optional.of(mapping));

        when(repository.save(any(UrlMapping.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UrlMapping result =
                service.resolve(SHORT_CODE);

        assertEquals(1, result.getClickCount());
    }

    @Test
    void shouldPropagateRepositoryExceptionDuringResolve() {

        when(repository.findByShortCode(SHORT_CODE))
                .thenThrow(new RuntimeException("DB failure"));

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> service.resolve(SHORT_CODE)
                );

        assertEquals("DB failure", exception.getMessage());
    }

    // ---------------------------------------------------------
    // ANALYTICS
    // ---------------------------------------------------------

    @Test
    void shouldReturnAnalytics() {

        mapping.setClickCount(25);

        when(repository.findByShortCode(SHORT_CODE))
                .thenReturn(Optional.of(mapping));

        AnalyticsResponse response =
                service.analytics(SHORT_CODE);

        assertNotNull(response);
        assertEquals(SHORT_CODE, response.getShortCode());
        assertEquals(LONG_URL, response.getLongUrl());
        assertEquals(25, response.getClickCount());
        assertFalse(response.isDisabled());
        assertFalse(response.isExpired());
    }

    @Test
    void shouldReturnAnalyticsForExpiredUrl() {

        mapping.setExpiresAt(
                Instant.now().minusSeconds(60)
        );

        when(repository.findByShortCode(SHORT_CODE))
                .thenReturn(Optional.of(mapping));

        AnalyticsResponse response =
                service.analytics(SHORT_CODE);

        assertTrue(response.isExpired());
    }

    @Test
    void shouldReturnAnalyticsForNonExpiredUrl() {

        mapping.setExpiresAt(
                Instant.now().plusSeconds(3600)
        );

        when(repository.findByShortCode(SHORT_CODE))
                .thenReturn(Optional.of(mapping));

        AnalyticsResponse response =
                service.analytics(SHORT_CODE);

        assertFalse(response.isExpired());
    }

    @Test
    void shouldReturnAnalyticsForDisabledUrl() {

        mapping.setDisabled(true);

        when(repository.findByShortCode(SHORT_CODE))
                .thenReturn(Optional.of(mapping));

        AnalyticsResponse response =
                service.analytics(SHORT_CODE);

        assertTrue(response.isDisabled());
    }

    @Test
    void shouldThrowWhenAnalyticsShortCodeDoesNotExist() {

        when(repository.findByShortCode(SHORT_CODE))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> service.analytics(SHORT_CODE)
        );
    }

    @Test
    void shouldPropagateAnalyticsRepositoryException() {

        when(repository.findByShortCode(SHORT_CODE))
                .thenThrow(new RuntimeException("DB failure"));

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> service.analytics(SHORT_CODE)
                );

        assertEquals("DB failure", exception.getMessage());
    }

    // ---------------------------------------------------------
    // DELETE
    // ---------------------------------------------------------

    @Test
    void shouldDeleteUrl() {

        when(repository.findByShortCode(SHORT_CODE))
                .thenReturn(Optional.of(mapping));

        service.delete(SHORT_CODE);

        verify(repository).delete(mapping);
    }

    @Test
    void shouldThrowWhenDeletingUnknownUrl() {

        when(repository.findByShortCode(SHORT_CODE))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> service.delete(SHORT_CODE)
        );

        verify(repository, never()).delete(any());
    }

    @Test
    void shouldPropagateDeleteRepositoryException() {

        when(repository.findByShortCode(SHORT_CODE))
                .thenReturn(Optional.of(mapping));

        doThrow(new RuntimeException("Delete failed"))
                .when(repository)
                .delete(mapping);

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> service.delete(SHORT_CODE)
                );

        assertEquals("Delete failed", exception.getMessage());
    }
}