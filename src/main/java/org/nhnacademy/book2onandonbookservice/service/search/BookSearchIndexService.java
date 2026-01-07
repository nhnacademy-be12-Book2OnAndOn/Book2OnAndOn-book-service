package org.nhnacademy.book2onandonbookservice.service.search;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.client.OllamaApiClient;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookPublisher;
import org.nhnacademy.book2onandonbookservice.entity.BookTag;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.entity.Tag;
import org.nhnacademy.book2onandonbookservice.repository.BookSearchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

// Book - ES 인덱싱 서비스
@Service
@RequiredArgsConstructor
@Slf4j
public class BookSearchIndexService {
    private final BookSearchRepository bookSearchRepository;
    private final OllamaApiClient ollamaApiClient;

    private static final int MAX_TEXT_LENGTH = 3000;

    // Book 엔티티를 ES 인덱스에 저장/갱신
    @Transactional
    public void index(Book book) {
        try{
            BookSearchDocument doc = createDocumentWithoutEmbedding(book);
            injectEmbedding(doc);
            bookSearchRepository.save(doc);
            log.info("🥳인덱싱 완료: {}", doc.getTitle());
        } catch (Exception e) {
            log.error("🥲인덱싱 실패 book id={}", book.getId(), e);
        }

    }

    // 도서 삭제 시 ES 인덱스에서도 같이 삭제
    public void deleteIndex(Long bookId) {
        bookSearchRepository.deleteById(bookId);
    }

    public BookSearchDocument createDocument(Book book) {
        BookSearchDocument doc = createDocumentWithoutEmbedding(book);
        injectEmbedding(doc);
        return doc;
    }

    /**
     * [Step 1] DB 조회 전용 (임베딩 X) -> 순차 처리용
     */
    public BookSearchDocument createDocumentWithoutEmbedding(Book book) {
        String thumbnail = book.getThumbnail();
        if (!StringUtils.hasText(thumbnail) && book.getImages() != null && !book.getImages().isEmpty()) {
            thumbnail = book.getImages().iterator().next().getImagePath();
        }

        List<String> categoryNames = new ArrayList<>();
        List<String> categoryIds = new ArrayList<>();
        Category category = book.getCategory();
        while (category != null) {
            categoryNames.add(0, category.getCategoryName());
            categoryIds.add(0, String.valueOf(category.getId()));
            category = category.getParent();
        }

        List<String> contributorNames = book.getBookContributors().stream()
                .map(bc -> bc.getContributor().getContributorName()).toList();
        List<String> publisherNames = book.getBookPublishers().stream()
                .map(BookPublisher::getPublisher).map(p -> p.getPublisherName()).toList();
        List<String> tagNames = book.getBookTags().stream()
                .map(BookTag::getTag).map(Tag::getTagName).toList();

        double reviewRating = (book.getRating() != null) ? book.getRating() : 0.0;
        long reviewCount = (book.getReviews() != null) ? book.getReviews().size() : 0L;
        long popularity = (book.getLikeCount() != null) ? book.getLikeCount() : 0L;

        return BookSearchDocument.builder()
                .id(book.getId())
                .isbn(book.getIsbn())
                .title(book.getTitle())
                .description(stripHtml(book.getDescription()))
                .volume(book.getVolume())
                .imagePath(thumbnail)
                .contributorNames(contributorNames)
                .publisherNames(publisherNames)
                .categoryNames(categoryNames)
                .categoryIds(categoryIds)
                .tagNames(tagNames)
                .publishDate(book.getPublishDate())
                .priceStandard(book.getPriceStandard())
                .priceSales(book.getPriceSales())
                .status(book.getStatus())
                .popularity(popularity)
                .reviewCount(reviewCount)
                .reviewRating(reviewRating)
                .embedding(null) // ★ 임베딩은 null로 둠
                .build();
    }

    /**
     * [Step 2] AI 호출 전용 (DB 접근 X) -> 병렬 처리용
     */
    public void injectEmbedding(BookSearchDocument doc) {
        String searchText = buildSearchText(doc);
        try {
            List<Float> vector = ollamaApiClient.getEmbedding(searchText);
            if (vector != null && !vector.isEmpty()) {
                doc.setEmbedding(vector);
            }
        } catch (Exception e) {
            log.warn("임베딩 생성 오류 (BookId: {}): {}", doc.getId(), e.getMessage());
        }
    }

    private String buildSearchText(BookSearchDocument doc) {
        StringBuilder sb = new StringBuilder();
        sb.append("제목: ").append(doc.getTitle()).append("\n");
        if (doc.getContributorNames() != null) sb.append("저자: ").append(String.join(", ", doc.getContributorNames())).append("\n");
        if (doc.getTagNames() != null) sb.append("태그: ").append(String.join(", ", doc.getTagNames())).append("\n");
        if (StringUtils.hasText(doc.getDescription())) sb.append("설명: ").append(doc.getDescription());
        return sb.toString();
    }

    private String stripHtml(String html){
        if(!StringUtils.hasText(html)){
            return "";
        }

        return html.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
    }
}
