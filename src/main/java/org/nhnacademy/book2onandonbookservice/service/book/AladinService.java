package org.nhnacademy.book2onandonbookservice.service.book;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.client.AladinCreateClient;
import org.nhnacademy.book2onandonbookservice.client.GeminiBookCreateClient;
import org.nhnacademy.book2onandonbookservice.dto.api.AladinApiResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSaveRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AladinService {

    private final AladinCreateClient aladinClient;
    private final GeminiBookCreateClient geminiBookCreateClient;

    /**
     * 알라딘 API를 통해 도서 정보를 조회하고, 프론트엔드 폼에 맞는 DTO로 변환
     */
    public BookSaveRequest searchBookInfo(String isbn) {
        AladinApiResponse.Item item = null;
        try {
            item = aladinClient.searchByIsbn(isbn);
        } catch (Exception e) {
            log.error("알라딘 검색 실패: {}", e.getMessage());
            throw new RuntimeException("도서 정보를 불러오는 데 실패했습니다 (알라딘 API 오류).");
        }

        if (item == null) {
            throw new RuntimeException("해당 ISBN으로 검색된 도서가 없습니다 (알라딘): " + isbn);
        }


        String generatedChapter = "";
        try {
            generatedChapter = geminiBookCreateClient.generateChapter(isbn, item.getTitle(), item.getDescription());
        } catch (Exception e) {
            log.warn("Gemini 목차 생성 실패 (무시하고 진행함): {}", e.getMessage());
        }

        return mapToBookSaveRequest(isbn, item, generatedChapter);
    }

    private BookSaveRequest mapToBookSaveRequest(String isbn, AladinApiResponse.Item item, String chapter) {
        String imageUrl = "";
        if (StringUtils.hasText(item.getCover())) {
            imageUrl = item.getCover().replace("http://", "https://");
        }

        LocalDate pubDate = parseDate(item.getPubDate());

        return BookSaveRequest.builder()
                .isbn(isbn)
                .title(item.getTitle())
                .volume("")
                .contributorName(item.getAuthor())
                .publisherName(item.getPublisher())
                .publishDate(pubDate)
                .descriptionHtml(item.getDescription())
                .chapter(chapter)
                .imageUrl(imageUrl)
                .priceStandard(item.getPriceStandard())
                .priceSales(item.getPriceSales())
                .stockCount(0)
                .isWrapped(false)
                .build();
    }

    private LocalDate parseDate(String dateStr) {
        if (!StringUtils.hasText(dateStr)) return LocalDate.now();
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ISO_DATE);
        } catch (Exception e) {
            try {
                if (dateStr.length() == 4) {
                    return LocalDate.of(Integer.parseInt(dateStr), 1, 1);
                }
            } catch (NumberFormatException ignored) {}
            log.warn("날짜 파싱 실패 ({}): {}", dateStr, e.getMessage());
            return LocalDate.now();
        }
    }
}