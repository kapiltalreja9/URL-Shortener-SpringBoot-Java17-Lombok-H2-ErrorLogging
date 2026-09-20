package com.example.urlshortener.controller;

import com.example.urlshortener.dto.AnalyticsResponse;
import com.example.urlshortener.dto.CreateUrlRequest;
import com.example.urlshortener.dto.CreateUrlResponse;
import com.example.urlshortener.entity.UrlMapping;
import com.example.urlshortener.service.UrlShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@RequiredArgsConstructor
@Slf4j
public class UrlController {

    private final UrlShortenerService service;

    @PostMapping("/api/v1/urls")
    public ResponseEntity<CreateUrlResponse> create(
            @Valid @RequestBody CreateUrlRequest request,
            HttpServletRequest httpRequest) {

        try {
            log.info("Create URL request received longUrl={}", request.getLongUrl());

            String baseUrl = httpRequest.getScheme() + "://" +
                    httpRequest.getServerName() +
                    (httpRequest.getServerPort() == 80 || httpRequest.getServerPort() == 443
                            ? ""
                            : ":" + httpRequest.getServerPort());

            CreateUrlResponse response = service.create(request, baseUrl);

            log.info("Create URL request completed shortCode={}", response.getShortCode());
            return ResponseEntity.ok(response);

        } catch (Exception ex) {
            log.error(
                    "Error creating short URL for longUrl={}",
                    request.getLongUrl(),
                    ex
            );
            throw ex;
        }
    }

    @GetMapping("/{shortCode}")
    public RedirectView redirect(@PathVariable String shortCode) {
        try {
            log.info("Redirect request received shortCode={}", shortCode);

            UrlMapping mapping = service.resolve(shortCode);

            RedirectView view = new RedirectView(mapping.getLongUrl());
            view.setExposeModelAttributes(false);

            log.info(
                    "Redirect request completed shortCode={} destination={}",
                    shortCode,
                    mapping.getLongUrl()
            );

            return view;

        } catch (Exception ex) {
            log.error(
                    "Error resolving shortCode={}",
                    shortCode,
                    ex
            );
            throw ex;
        }
    }

    @GetMapping("/api/v1/urls/{shortCode}/analytics")
    public ResponseEntity<AnalyticsResponse> analytics(@PathVariable String shortCode) {
        try {
            log.info("Analytics request received shortCode={}", shortCode);

            AnalyticsResponse response = service.analytics(shortCode);

            log.info(
                    "Analytics request completed shortCode={} clickCount={}",
                    shortCode,
                    response.getClickCount()
            );

            return ResponseEntity.ok(response);

        } catch (Exception ex) {
            log.error(
                    "Error retrieving analytics for shortCode={}",
                    shortCode,
                    ex
            );
            throw ex;
        }
    }

    @DeleteMapping("/api/v1/urls/{shortCode}")
    public ResponseEntity<Void> delete(@PathVariable String shortCode) {
        try {
            log.info("Delete request received shortCode={}", shortCode);

            service.delete(shortCode);

            log.info("Delete request completed shortCode={}", shortCode);
            return ResponseEntity.noContent().build();

        } catch (Exception ex) {
            log.error(
                    "Error deleting shortCode={}",
                    shortCode,
                    ex
            );
            throw ex;
        }
    }
}
