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
            BookSearchDocument bookSearchDocument = createDocument(book);
            bookSearchRepository.save(bookSearchDocument);
            log.info("🥳인덱싱 완료: {}", bookSearchDocument.getTitle());
        } catch (Exception e) {
            log.error("🥲인덱싱 실패 book id={}", book.getId(), e);
        }

    }

    // 도서 삭제 시 ES 인덱스에서도 같이 삭제
    public void deleteIndex(Long bookId) {
        bookSearchRepository.deleteById(bookId);
    }

    public BookSearchDocument createDocument(Book book) {
        String thumbnail = book.getThumbnail();

        if (!StringUtils.hasText(thumbnail) && book.getImages() != null && !book.getImages().isEmpty()) {
            thumbnail = book.getImages().iterator().next().getImagePath();
        }

        List<String> categoryNames = new ArrayList<>();
        List<String> categoryIds = new ArrayList<>();

        Category category = book.getCategory();

        while (category != null) {
            categoryNames.add(0, category.getCategoryName()); // 앞에 추가 (루트가 먼저 오도록)
            categoryIds.add(0, String.valueOf(category.getId()));
            category = category.getParent();
        }

        List<String> contributorNames = book.getBookContributors().stream()
                .map(bc -> bc.getContributor().getContributorName())
                .toList();

        List<String> publisherNames = book.getBookPublishers().stream()
                .map(BookPublisher::getPublisher)
                .map(p -> p.getPublisherName())
                .toList();

        List<String> tagNames = book.getBookTags().stream()
                .map(BookTag::getTag)
                .map(Tag::getTagName)
                .toList();

        // 임베딩을 위한 통합 텍스트 생성 (제목 + 저자 + 설명 + 태그)
        String searchText = buildSearchText(book, contributorNames, tagNames);

        // Ollama를 통해 1024차원 벡터 생성
        List<Float> embedding = null; // 기본값 null
        try {
            List<Float> vector = ollamaApiClient.getEmbedding(searchText);

            // 벡터가 존재하고 비어있지 않을 때만 할당
            if (vector != null && !vector.isEmpty()) {
                embedding = vector;
            }
        } catch (Exception e) {
            log.warn("임베딩 생성 중 오류 발생 (BookId: {}): {}", book.getId(), e.getMessage());
            // 실패하면 embedding은 null 상태 유지 -> ES가 에러 없이 넘어감
        }
        double reviewRating = (book.getRating() != null) ? book.getRating() : 0.0;
        long reviewCount = (book.getReviews() != null) ? book.getReviews().size(): 0L;

        long popularity = (book.getLikeCount() != null) ? book.getLikeCount() : 0L;

        return BookSearchDocument.builder()
                .id(book.getId())
                .isbn(book.getIsbn())
                .title(book.getTitle())
                .description(stripHtml(book.getDescription())) // 설명 필드 매핑
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
                // 정렬/가중치 필드 (엔티티에 없으면 0으로 초기화하거나 계산 로직 필요)
                .popularity(popularity) // 좋아요 순
                .reviewCount(reviewCount) //리뷰 수
                .reviewRating(reviewRating) //평점
                .embedding(embedding) //벡터 주입
                .build();
    }

    private String buildSearchText(Book book , List<String> authors, List<String> tags){
        StringBuilder sb = new StringBuilder();

        sb.append("제목: ").append(book.getTitle()).append("\n");

        if (!authors.isEmpty()) {
            sb.append("저자: ").append(String.join(", ", authors)).append("\n");
        }

        if (!tags.isEmpty()) {
            sb.append("태그: ").append(String.join(", ", tags)).append("\n");
        }

        String description = book.getDescription();
        if (StringUtils.hasText(description)) {
            // HTML 제거 및 길이 자르기
            String cleanDescription = stripHtml(description);
            if (cleanDescription.length() > MAX_TEXT_LENGTH) {
                cleanDescription = cleanDescription.substring(0, MAX_TEXT_LENGTH);
            }
            sb.append("설명: ").append(cleanDescription);
        }

        return sb.toString();
    }

    private String stripHtml(String html){
        if(!StringUtils.hasText(html)){
            return "";
        }

        return html.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
    }
}
