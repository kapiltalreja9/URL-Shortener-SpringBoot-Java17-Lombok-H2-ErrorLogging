package com.example.urlshortener.controller;

import com.example.urlshortener.dto.AnalyticsResponse;
import com.example.urlshortener.dto.CreateUrlResponse;
import com.example.urlshortener.entity.UrlMapping;
import com.example.urlshortener.exception.BadRequestException;
import com.example.urlshortener.exception.NotFoundException;
import com.example.urlshortener.exception.UrlDisabledException;
import com.example.urlshortener.exception.UrlExpiredException;
import com.example.urlshortener.service.UrlShortenerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.view.RedirectView;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UrlController.class)
public class UrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UrlShortenerService service;

    // ---------------------------------------------------------
    // CREATE
    // ---------------------------------------------------------

    @Test
    void shouldCreateShortUrl() throws Exception {

        CreateUrlResponse response =
                CreateUrlResponse.builder()
                        .shortCode("abc123")
                        .shortUrl("http://localhost:8080/abc123")
                        .longUrl("https://example.com")
                        .createdAt(Instant.now())
                        .build();

        when(service.create(any(), any()))
                .thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/urls")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "longUrl": "https://example.com"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("abc123"))
                .andExpect(jsonPath("$.shortUrl")
                        .value("http://localhost:8080/abc123"))
                .andExpect(jsonPath("$.longUrl")
                        .value("https://example.com"));

        verify(service).create(any(), any());
    }

    @Test
    void shouldCreateUrlWithExpiration() throws Exception {

        Instant expiresAt = Instant.now().plusSeconds(3600);

        CreateUrlResponse response =
                CreateUrlResponse.builder()
                        .shortCode("abc123")
                        .shortUrl("http://localhost:8080/abc123")
                        .longUrl("https://example.com")
                        .createdAt(Instant.now())
                        .expiresAt(expiresAt)
                        .build();

        when(service.create(any(), any()))
                .thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/urls")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "longUrl": "https://example.com",
                                          "expiresAt": "2030-01-01T00:00:00Z"
                                        }
                                        """)
                )
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectBlankLongUrl() throws Exception {

        mockMvc.perform(
                        post("/api/v1/urls")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "longUrl": ""
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());

        verify(service, never()).create(any(), any());
    }

    @Test
    void shouldRejectMissingLongUrl() throws Exception {

        mockMvc.perform(
                        post("/api/v1/urls")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isBadRequest());

        verify(service, never()).create(any(), any());
    }

    @Test
    void shouldHandleBadRequestFromService() throws Exception {

        when(service.create(any(), any()))
                .thenThrow(
                        new BadRequestException(
                                "longUrl must be a valid HTTP or HTTPS URL"
                        )
                );

        mockMvc.perform(
                        post("/api/v1/urls")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "longUrl": "bad-url"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    // ---------------------------------------------------------
    // REDIRECT
    // ---------------------------------------------------------

    @Test
    void shouldRedirectToLongUrl() throws Exception {

        UrlMapping mapping =
                UrlMapping.builder()
                        .shortCode("abc123")
                        .longUrl("https://example.com")
                        .createdAt(Instant.now())
                        .clickCount(1)
                        .disabled(false)
                        .build();

        when(service.resolve("abc123"))
                .thenReturn(mapping);

        mockMvc.perform(
                        get("/abc123")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(
                        redirectedUrl("https://example.com")
                );

        verify(service).resolve("abc123");
    }

    @Test
    void shouldReturn404ForUnknownShortCode() throws Exception {

        when(service.resolve("unknown"))
                .thenThrow(
                        new NotFoundException(
                                "Short URL not found: unknown"
                        )
                );

        mockMvc.perform(
                        get("/unknown")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void shouldReturn410ForExpiredUrl() throws Exception {

        when(service.resolve("abc123"))
                .thenThrow(
                        new UrlExpiredException(
                                "Short URL has expired: abc123"
                        )
                );

        mockMvc.perform(
                        get("/abc123")
                )
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410));
    }

    @Test
    void shouldReturn410ForDisabledUrl() throws Exception {

        when(service.resolve("abc123"))
                .thenThrow(
                        new UrlDisabledException(
                                "Short URL is disabled: abc123"
                        )
                );

        mockMvc.perform(
                        get("/abc123")
                )
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410));
    }

    @Test
    void shouldHandleUnexpectedRedirectException() throws Exception {

        when(service.resolve("abc123"))
                .thenThrow(new RuntimeException("Unexpected error"));

        mockMvc.perform(
                        get("/abc123")
                )
                .andExpect(status().isInternalServerError());
    }

    // ---------------------------------------------------------
    // ANALYTICS
    // ---------------------------------------------------------

    @Test
    void shouldReturnAnalytics() throws Exception {

        AnalyticsResponse response =
                AnalyticsResponse.builder()
                        .shortCode("abc123")
                        .longUrl("https://example.com")
                        .createdAt(Instant.now())
                        .clickCount(10)
                        .disabled(false)
                        .expired(false)
                        .build();

        when(service.analytics("abc123"))
                .thenReturn(response);

        mockMvc.perform(
                        get("/api/v1/urls/abc123/analytics")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("abc123"))
                .andExpect(jsonPath("$.clickCount").value(10))
                .andExpect(jsonPath("$.expired").value(false));
    }

    @Test
    void shouldReturn404WhenAnalyticsNotFound() throws Exception {

        when(service.analytics("abc123"))
                .thenThrow(
                        new NotFoundException(
                                "Short URL not found: abc123"
                        )
                );

        mockMvc.perform(
                        get("/api/v1/urls/abc123/analytics")
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldHandleAnalyticsUnexpectedException() throws Exception {

        when(service.analytics("abc123"))
                .thenThrow(new RuntimeException("DB failure"));

        mockMvc.perform(
                        get("/api/v1/urls/abc123/analytics")
                )
                .andExpect(status().isInternalServerError());
    }

    // ---------------------------------------------------------
    // DELETE
    // ---------------------------------------------------------

    @Test
    void shouldDeleteUrl() throws Exception {

        doNothing()
                .when(service)
                .delete("abc123");

        mockMvc.perform(
                        delete("/api/v1/urls/abc123")
                )
                .andExpect(status().isNoContent());

        verify(service).delete("abc123");
    }

    @Test
    void shouldReturn404WhenDeletingUnknownUrl() throws Exception {

        doThrow(
                new NotFoundException(
                        "Short URL not found: abc123"
                )
        )
                .when(service)
                .delete("abc123");

        mockMvc.perform(
                        delete("/api/v1/urls/abc123")
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldHandleUnexpectedDeleteException() throws Exception {

        doThrow(new RuntimeException("Database failure"))
                .when(service)
                .delete("abc123");

        mockMvc.perform(
                        delete("/api/v1/urls/abc123")
                )
                .andExpect(status().isInternalServerError());
    }

    @Test
    void shouldHandleMalformedJson() throws Exception {

        mockMvc.perform(
                        post("/api/v1/urls")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "longUrl":
                                    """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message")
                        .value("Malformed request body"))
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/urls"));

        verify(service, never()).create(any(), any());
    }
}