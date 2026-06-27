package com.example.tribeo.controller;

import com.example.tribeo.security.response.MessageResponse;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<MessageResponse> handleInvalidRequestBody(HttpMessageNotReadableException ex) {
        Optional<InvalidFormatException> invalidFormatException = findCause(ex, InvalidFormatException.class);

        if (invalidFormatException.isPresent()
                && invalidFormatException.get().getTargetType().equals(LocalDate.class)
                && hasField(invalidFormatException.get(), "dateOfBirth")) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("dateOfBirth must be in yyyy-MM-dd format"));
        }

        return ResponseEntity.badRequest().body(new MessageResponse("Invalid request body"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MessageResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getDefaultMessage())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("Invalid request body");

        return ResponseEntity.badRequest().body(new MessageResponse(message));
    }

    private boolean hasField(InvalidFormatException ex, String fieldName) {
        return ex.getPath().stream()
                .map(JsonMappingException.Reference::getFieldName)
                .anyMatch(fieldName::equals);
    }

    private <T extends Throwable> Optional<T> findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return Optional.of(type.cast(current));
            }
            current = current.getCause();
        }
        return Optional.empty();
    }
}
