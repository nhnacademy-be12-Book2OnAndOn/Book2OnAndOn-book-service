package org.nhnacademy.book2onandonbookservice.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.dto.error.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler globalExceptionHandler;

    @Test
    void handleValidationException() {
        String validationMessage = "must not be null";
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        ObjectError objectError = mock(ObjectError.class);

        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(List.of(objectError));
        when(objectError.getDefaultMessage()).thenReturn(validationMessage);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleValidationException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getError()).isEqualTo("Bad Request");
        assertThat(response.getBody().getMessage()).isEqualTo(validationMessage);
    }

    @Test
    void handleAccessDeniedException() {
        String errorMessage = "Access is denied";
        AccessDeniedException exception = new AccessDeniedException(errorMessage);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleAccessDeniedException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(403);
        assertThat(response.getBody().getError()).isEqualTo("Forbidden");
        assertThat(response.getBody().getMessage()).isEqualTo(errorMessage);
    }

    @Test
    void handleAccessDeniedException_BookNotPurchased() {
        BookNotPurchasedException exception = new BookNotPurchasedException();

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleAccessDeniedException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getStatus()).isEqualTo(403);
        assertThat(response.getBody().getError()).isEqualTo("Forbidden");
    }

    @Test
    void handleNotFoundException() {
        NotFoundBookException exception = new NotFoundBookException(1L);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleNotFoundException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getError()).isEqualTo("Not Found");
        assertThat(response.getBody().getMessage()).contains("1");
    }

    @Test
    void handleOutOfStockException() {
        String errorMessage = "재고가 부족합니다";
        OutOfStockException exception = new OutOfStockException(errorMessage);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleOutOfStockException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(409);
        assertThat(response.getBody().getError()).isEqualTo("Stock Conflict");
        assertThat(response.getBody().getMessage()).isEqualTo(errorMessage);
    }

    @Test
    void handleReviewAlreadyExistsException() {
        ReviewAlreadyExistsException exception = new ReviewAlreadyExistsException();
        String expectedMessage = "이미 해당 도서에 대한 리뷰를 작성했습니다.";

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleReviewAlreadyExistsException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(409);
        assertThat(response.getBody().getError()).isEqualTo("Review Conflict");
        assertThat(response.getBody().getMessage()).isEqualTo(expectedMessage);
    }

    @Test
    void handleQuotaExceptions() {
        String errorMessage = "Quota exceeded";
        GroqQuotaExceededException exception = new GroqQuotaExceededException(errorMessage);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleQuotaExceptions(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(429);
        assertThat(response.getBody().getError()).isEqualTo("Too Many Requests");
        assertThat(response.getBody().getMessage()).isEqualTo(errorMessage);
    }

    @Test
    void handleInternalLogicExceptions() {
        String errorMessage = "Upload failed";
        ImageUploadException exception = new ImageUploadException(errorMessage, new RuntimeException());

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleInternalLogicExceptions(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getError()).isEqualTo("Internal Processing Error");
        assertThat(response.getBody().getMessage()).isEqualTo(errorMessage);
    }

    @Test
    void handleInternalLogicExceptions_TagGeneration() {
        String errorMessage = "Tag Logic Failed";
        TagGenerationFailedException exception = new TagGenerationFailedException(errorMessage);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleInternalLogicExceptions(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getMessage()).isEqualTo(errorMessage);
    }

    @Test
    void handleApiException() {
        String errorMessage = "External API Error";
        AladinApiException exception = new AladinApiException(errorMessage);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleApiException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(502);
        assertThat(response.getBody().getError()).isEqualTo("External API Error");
    }

    @Test
    void handleApiException_Gemini() {
        String errorMessage = "Gemini API Connection Failed";
        GeminiTagGenerationException exception = new GeminiTagGenerationException(errorMessage, new RuntimeException());

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleApiException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().getStatus()).isEqualTo(502);
        assertThat(response.getBody().getError()).isEqualTo("External API Error");
    }

    @Test
    void handleVerificationException() {
        String errorMessage = "Verification failed";
        PurchaseVerificationUnavailableException exception = new PurchaseVerificationUnavailableException(errorMessage);

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleVerificationException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(503);
        assertThat(response.getBody().getError()).isEqualTo("Service Unavailable");
        assertThat(response.getBody().getMessage()).isEqualTo("일시적인 서비스 장애로 요청을 처리할 수 없습니다.");
    }

    @Test
    void handleGlobalException() {
        Exception exception = new RuntimeException("Unexpected error");

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGlobalException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getError()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().getMessage()).isEqualTo("서버 내부 오류가 발생했습니다. 관리자에게 문의하세요.");
    }
}