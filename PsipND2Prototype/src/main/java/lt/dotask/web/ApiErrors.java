package lt.dotask.web;

import com.fasterxml.jackson.databind.JsonMappingException;
import lt.dotask.application.DemoResetException;
import lt.dotask.application.RecordNotFoundException;
import lt.dotask.domain.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/** Java klaidas paverčia vienodo formato lietuviškais HTTP atsakymais be techninių paslapčių. */
@RestControllerAdvice
public class ApiErrors {
    // Aiški koduotė reikalinga ir senesniam Windows PowerShell, kad lietuviškos raidės išliktų.
    private static final MediaType JSON_UTF8 = new MediaType("application", "json", StandardCharsets.UTF_8);

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, String>> validation(ValidationException error) { return error(HttpStatus.BAD_REQUEST, error.getMessage()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> fields(MethodArgumentNotValidException error) {
        return error(HttpStatus.BAD_REQUEST, error.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage()).collect(Collectors.joining("; ")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> json(HttpMessageNotReadableException exception) {
        String field = "JSON";
        if (exception.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            field = mapping.getPath().getLast().getFieldName();
        }
        return error(HttpStatus.BAD_REQUEST, field + ": netinkamas arba neleistinas laukas. Patikrinkite laukų pavadinimus ir reikšmių tipus.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> parameter(MethodArgumentTypeMismatchException exception) { return error(HttpStatus.BAD_REQUEST, exception.getName() + ": netinkamas identifikatorius."); }

    @ExceptionHandler(RecordNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(RecordNotFoundException exception) { return error(HttpStatus.NOT_FOUND, exception.getMessage()); }

    @ExceptionHandler(DemoResetException.class)
    public ResponseEntity<Map<String, String>> reset(DemoResetException exception) { return error(HttpStatus.CONFLICT, exception.getMessage()); }

    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<Map<String, String>> unknownRoute(Exception exception) { return error(HttpStatus.NOT_FOUND, "Toks API adresas arba veiksmas nerastas."); }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, String>> unsupportedContentType(Exception exception) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type: siųskite duomenis application/json formatu.");
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Map<String, String>> unsupportedResponseType(Exception exception) {
        return error(HttpStatus.NOT_ACCEPTABLE, "Accept: API atsakymas pateikiamas application/json formatu.");
    }

    @ExceptionHandler(Exception.class)
    // Netikėtos klaidos detalių nerodome puslapyje, nes jos gali atskleisti vidinę infrastruktūrą.
    public ResponseEntity<Map<String, String>> unexpected(Exception exception) { return error(HttpStatus.INTERNAL_SERVER_ERROR, "Veiksmo nepavyko įvykdyti. Ankstesni duomenys išsaugoti. Patikrinkite serverio ir duomenų bazės veikimą."); }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) { return ResponseEntity.status(status).contentType(JSON_UTF8).body(Map.of("message", message)); }
}
