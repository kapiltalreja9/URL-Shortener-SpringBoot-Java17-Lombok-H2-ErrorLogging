package com.example.urlshortener.exception;

public class UrlDisabledException extends RuntimeException {
    public UrlDisabledException(String message) {
        super(message);
    }
}
