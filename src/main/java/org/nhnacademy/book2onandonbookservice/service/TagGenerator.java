package org.nhnacademy.book2onandonbookservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.client.GeminiApiClient;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookTag;
import org.nhnacademy.book2onandonbookservice.entity.Tag;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookTagRepository;
import org.nhnacademy.book2onandonbookservice.repository.TagRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TagGenerator {

    private final BookRepository bookRepository;
    private final TagRepository tagRepository;
    private final BookTagRepository bookTagRepository;
    private final GeminiApiClient geminiApiClient;
    private final ObjectMapper objectMapper;

    /**
     * 태그 생성 작업을 실행합니다. (스케줄러나 컨트롤러에서 호출)
     *
     * @param batchSize 한 번에 처리할 책의 개수 (예: 10개)
     */
    public void generateTagsForBooks(int batchSize) {
        // 1. 태그가 없는 책을 batchSize만큼 조회 (쿼리 최적화 필요 시 JPQL 사용 권장)
        // 여기서는 단순하게 모든 책을 페이지 단위로 가져와서 태그 여부를 확인하는 방식으로 예시를 듭니다.
        // 실제로는 bookRepository.findBooksWithoutTags() 같은 커스텀 쿼리가 더 좋습니다.

        Pageable pageable = PageRequest.of(0, batchSize);
        Page<Book> bookPage = bookRepository.findBooksWithoutTags(pageable);

        log.info("태그 생성 작업 시작 (대상: {}권)", bookPage.getNumberOfElements());

        for (Book book : bookPage.getContent()) {

            processSingleBookTag(book);

            //  API Rate Limit 방지 (Gemini 무료: 분당 15회 제한)
            // 1권 처리 후 4초 대기 -> 분당 15회 이하 유지
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("태그 생성 작업 배치가 완료되었습니다.");
    }

    @Transactional
    public void processSingleBookTag(Book book) {
        String title = book.getTitle();
        String description = book.getDescription();

        // 프롬프트 생성
        String prompt = String.format(
                "Recommend 5 relevant hashtags (Korean) for a book titled '%s'. " +
                        "Description: '%s'. " +
                        "Return ONLY a JSON array of strings. Example: [\"#소설\", \"#힐링\", \"#베스트셀러\"]",
                title, (description != null ? description.substring(0, Math.min(description.length(), 200)) : "")
        );

        try {
            String jsonResponse = geminiApiClient.generateContent(prompt);

            if (jsonResponse == null || jsonResponse.isBlank()) {
                log.warn("Gemini 응답 없음 (BookId: {})", book.getId());
                return;
            }

            // 마크다운 코드 블록 제거 (```json ... ```)
            jsonResponse = jsonResponse.replace("```json", "").replace("```", "").trim();

            // JSON 파싱 (List<String>)
            List<String> tagNames = objectMapper.readValue(jsonResponse, new TypeReference<List<String>>() {
            });

            for (String tagName : tagNames) {
                // 태그 정제 (# 제거, 공백 제거)
                String cleanedTagName = tagName.replace("#", "").trim();
                if (cleanedTagName.isEmpty()) {
                    continue;
                }

                // 태그 저장 또는 조회 (중복 방지)
                Tag tag = tagRepository.findByTagName(cleanedTagName)
                        .orElseGet(() -> tagRepository.save(Tag.builder().tagName(cleanedTagName).build()));

                // BookTag 연결 및 저장
                BookTag bookTag = BookTag.builder()
                        .book(book)
                        .tag(tag)
                        .build();

                bookTagRepository.save(bookTag);
            }
            log.info("태그 저장 완료 (BookId: {}, Tags: {})", book.getId(), tagNames);

        } catch (Exception e) {
            log.error("태그 생성 실패 (BookId: {}): {}", book.getId(), e.getMessage());
        }
    }
}