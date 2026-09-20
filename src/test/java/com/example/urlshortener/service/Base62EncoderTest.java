package com.example.urlshortener.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Base62EncoderTest {

    private Base62Encoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new Base62Encoder();
    }

    @Test
    void shouldEncodeZero() {
        assertEquals("0", encoder.encode(0));
    }

    @Test
    void shouldEncodeSingleDigitValues() {
        assertEquals("0", encoder.encode(0));
        assertEquals("1", encoder.encode(1));
        assertEquals("9", encoder.encode(9));
    }

    @Test
    void shouldEncodeLowercaseCharacters() {
        assertEquals("a", encoder.encode(10));
        assertEquals("z", encoder.encode(35));
    }

    @Test
    void shouldEncodeUppercaseCharacters() {
        assertEquals("A", encoder.encode(36));
        assertEquals("Z", encoder.encode(61));
    }

    @Test
    void shouldEncodeValuesGreaterThanBase62() {
        assertEquals("10", encoder.encode(62));
        assertEquals("11", encoder.encode(63));
        assertEquals("100", encoder.encode(62 * 62));
    }

    @Test
    void shouldEncodeLargeValue() {
        String result = encoder.encode(Long.MAX_VALUE);

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void shouldRejectNegativeValue() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> encoder.encode(-1)
                );

        assertEquals("value must be non-negative", exception.getMessage());
    }
}