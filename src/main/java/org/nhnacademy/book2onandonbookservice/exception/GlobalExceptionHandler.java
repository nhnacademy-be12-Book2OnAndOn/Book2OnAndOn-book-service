package org.nhnacademy.book2onandonbookservice.exception;

import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.dto.error.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * [400] @Valid 유효성 검사 실패 시 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                errorMessage
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * [403 Forbidden] 접근 권한 없음
     */
    @ExceptionHandler({AccessDeniedException.class, BookNotPurchasedException.class})
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(Exception e) {
        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                e.getMessage()
        );
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    /**
     * [404 Not Found] 리소스 미발견
     */
    @ExceptionHandler({
            NotFoundBookException.class,
            NotFoundReviewException.class,
            NotFoundCategoryException.class,
            NotFoundTagException.class
    })
    public ResponseEntity<ErrorResponse> handleNotFoundException(Exception ex) {
        log.warn("[NOT FOUND] {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage()
        );
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    /**
     * [409 Conflict] 재고 부족
     */
    @ExceptionHandler(OutOfStockException.class)
    public ResponseEntity<ErrorResponse> handleOutOfStockException(OutOfStockException e) {
        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "Stock Conflict",
                e.getMessage()
        );
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    /**
     * [409 Conflict] 이미 리뷰 존재
     */
    @ExceptionHandler(ReviewAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleReviewAlreadyExistsException(ReviewAlreadyExistsException e) {
        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "Review Conflict",
                e.getMessage()
        );
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    /**
     * [429 Too Many Requests] API 호출 한도 초과
     */
    @ExceptionHandler({GroqQuotaExceededException.class, GeminiQuotaExceededException.class})
    public ResponseEntity<ErrorResponse> handleQuotaExceptions(RuntimeException e) {
        log.warn("[API LIMIT] {}", e.getMessage());

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too Many Requests",
                e.getMessage()
        );
        return new ResponseEntity<>(response, HttpStatus.TOO_MANY_REQUESTS);
    }

    /**
     * [500 Internal Server Error] 내부 로직 처리 실패
     * (이미지 업로드, 카테고리 파싱, 태그 생성 로직 오류)
     */
    @ExceptionHandler({
            ImageUploadException.class,
            CategoryResolveException.class,
            TagGenerationFailedException.class // 여기는 로직 오류 (결과값 이상 등)
    })
    public ResponseEntity<ErrorResponse> handleInternalLogicExceptions(RuntimeException ex) {
        log.error("[INTERNAL LOGIC FAIL] {}", ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Processing Error",
                ex.getMessage()
        );
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(EmbeddingGenerationException.class)
    public ResponseEntity<ErrorResponse> handleEmbeddingException(EmbeddingGenerationException e) {
        log.error("Embedding Generation Error: {}", e.getMessage(), e); // 에러 로그 기록

        // 클라이언트에게는 상세 내용보다는 "검색 처리 중 오류" 정도로 알림
        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "EMBEDDING_ERROR",
                e.getMessage()
        );

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * [502 Bad Gateway] 외부 API 통신 실패
     * (알라딘, Gemini/Groq API 네트워크 오류)
     */
    @ExceptionHandler({
            AladinApiException.class,
            GeminiTagGenerationException.class, // 여기는 통신 오류 (호출 실패)
            GroqApiCallException.class,
    })
    public ResponseEntity<ErrorResponse> handleApiException(Exception e) {
        log.error("[EXTERNAL API ERROR] {}", e.getMessage());

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_GATEWAY.value(),
                "External API Error",
                "외부 서비스 통신 중 오류가 발생했습니다."
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_GATEWAY);
    }

    /**
     * [503 Service Unavailable] 타 서비스 장애
     */
    @ExceptionHandler(PurchaseVerificationUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleVerificationException(PurchaseVerificationUnavailableException ex) {
        log.error("[SERVICE UNAVAILABLE] {}", ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "Service Unavailable",
                "일시적인 서비스 장애로 요청을 처리할 수 없습니다."
        );
        return new ResponseEntity<>(response, HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * [500] 검색 엔진 에러
     */
    @ExceptionHandler(SearchExecutionException.class)
    public ResponseEntity<ErrorResponse> handleSearchExecutionException(SearchExecutionException e) {
        log.error("[Search Error] 검색 엔진 오류 발생: {}", e.getMessage(), e);

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "SEARCH_ENGINE_ERROR",
                "검색 서비스에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해주세요."
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    /**
     * [500 Internal Server Error] 최후의 안전망
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex) {
        log.error("[UNHANDLED EXCEPTION] ", ex);

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "서버 내부 오류가 발생했습니다. 관리자에게 문의하세요."
        );
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}