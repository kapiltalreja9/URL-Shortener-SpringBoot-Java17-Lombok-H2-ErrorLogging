package com.example.urlshortener.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class Base62Encoder {

    private static final char[] ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    public String encode(long value) {
        try {
            log.debug("Encoding value={} to Base62", value);

            if (value < 0) {
                throw new IllegalArgumentException("value must be non-negative");
            }

            if (value == 0) {
                return "0";
            }

            StringBuilder result = new StringBuilder();

            while (value > 0) {
                result.append(ALPHABET[(int) (value % 62)]);
                value /= 62;
            }

            String encoded = result.reverse().toString();

            log.debug("Base62 encoding completed encoded={}", encoded);
            return encoded;

        } catch (Exception ex) {
            log.error("Error encoding value={} to Base62", value, ex);
            throw ex;
        }
    }
}
