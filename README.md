# URL Shortener - Spring Boot / Java 17 / H2 / Lombok

## Features

- Create short URLs
- Base62 short-code generation from database-generated IDs
- HTTP/HTTPS URL validation
- Optional expiration
- 302 redirects
- Click-count analytics
- Disable/delete by deletion
- Global exception handling
- Full ERROR stack traces in a log file
- H2 file database; no external database required
- Spring Boot Actuator
- Graceful shutdown
- Lombok

## Requirements

- Java 17+
- Maven 3.9+

## Run

```bash
mvn clean package
java -jar target/url-shortener-1.0.0.jar
```

Or:

```bash
mvn spring-boot:run
```

## Database

H2 is file-based and stored under:

```text
./data/urlshortener
```

No `AUTO_SERVER=TRUE` or `DB_CLOSE_ON_EXIT=FALSE` options are used.

If changing from an older project, stop the application and remove the old `data` directory before first startup.

## Logs

Application logs are written to:

```text
logs/url-shortener.log
```

Rolling files are also created after the configured size limit.

The generic exception handler uses:

```java
log.error(
    "Unhandled exception while processing {} {}",
    request.getMethod(),
    request.getRequestURI(),
    ex
);
```

The final `ex` argument is intentional: it writes the complete stack trace and `Caused by` chain.

## APIs

### Create

```http
POST /api/v1/urls
Content-Type: application/json
```

Body:

```json
{
  "longUrl": "https://example.com",
  "expiresAt": "2027-01-01T00:00:00Z"
}
```

### Redirect

```http
GET /{shortCode}
```

Returns HTTP 302 with the `Location` header.

### Analytics

```http
GET /api/v1/urls/{shortCode}/analytics
```

### Delete

```http
DELETE /api/v1/urls/{shortCode}
```

### Actuator

```text
GET /actuator/health
GET /actuator/info
GET /actuator/metrics
```

## Lombok

The project uses Lombok annotations including:

- `@Getter`
- `@Setter`
- `@Builder`
- `@NoArgsConstructor`
- `@AllArgsConstructor`
- `@RequiredArgsConstructor`
- `@Slf4j`

Most modern Maven/IDE setups detect Lombok automatically. If using IntelliJ IDEA, enable annotation processing if Lombok-generated methods are not recognized.

## Troubleshooting a 500

1. Reproduce the request.
2. Open `logs/url-shortener.log`.
3. Search for `Unhandled exception`.
4. Inspect the `Caused by:` section.
5. The API intentionally returns a generic 500 response while the detailed stack trace remains in the server log.

Example:

```bash
tail -f logs/url-shortener.log
```


## Controller and Service Error Logging

Controllers and services use Lombok `@Slf4j` and wrap their operations with
`try/catch` blocks. Errors are logged with the full exception:

```java
catch (Exception ex) {
    log.error("Error processing request", ex);
    throw ex;
}
```

The exception is rethrown so `GlobalExceptionHandler` remains responsible for
the HTTP response. This avoids hiding the original error while preserving the
existing API error contract.

For example:

```java
@Slf4j
@RestController
@RequiredArgsConstructor
public class UrlController {
    // ...
}
```

and:

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class UrlShortenerService {
    // ...
}
```
