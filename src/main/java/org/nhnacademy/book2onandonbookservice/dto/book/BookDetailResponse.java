package org.nhnacademy.book2onandonbookservice.dto.book;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;
import org.nhnacademy.book2onandonbookservice.dto.common.CategoryDto;
import org.nhnacademy.book2onandonbookservice.dto.common.PublisherDto;
import org.nhnacademy.book2onandonbookservice.dto.common.TagDto;
import org.nhnacademy.book2onandonbookservice.dto.review.ReviewDto;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.nhnacademy.book2onandonbookservice.entity.Review;

// 도서 상세 페이지 출력
@Getter
@Builder
public class BookDetailResponse {
    private Long id;    // 도서 아이디
    private String isbn;    // isbn

    private String title;   // 도서 제목
    private String volume;  // 도서 권 제목

    private String contributorName; // 기여자 이름

    private LocalDate publishDate;  // 도서 출판일
    private List<PublisherDto> publishers;  // 출판사

    private Long priceStandard; // 도서 정가
    private Long priceSales; // 도서 판매가

    private String stockStatus; // 책 재고 상태

    private List<CategoryDto> categories;   // 카테고리
    private List<TagDto> tags;  // 태그
    private Boolean isWrapped;  // 포장 여부

    private String imagePath;   // 도서 이미지

    private String chapter; // 도서 목차

    private String descriptionHtml; // 도서 설명

    private Long likeCount; // 전체 좋아요 수
    private Boolean likedByCurrentUser; // 사용자가 좋아요를 눌렀는지의 여부

    private List<ReviewDto> reviews;   // 상위 몇 개만
    private Double rating;       // 평균 평점
    private Long reviewCount;          // 전체 리뷰 개수


    /// 헬퍼 메서드
    public static BookDetailResponse from(Book book, Long currentUserId) {
        //작가 이름 연결
        String contributors = book.getBookContributors().stream()
                .map(bc -> bc.getContributor().getContributorName())
                .collect(Collectors.joining(", "));

        //대표이미지 추출
        String thumbnail = book.getImages().stream()
                .findFirst()
                .map(BookImage::getImagePath)
                .orElse("/images/no-image.png");

        // 좋아요 여부 확인
        boolean isLiked = false;
        if (currentUserId != null) {
            isLiked = book.getLikes().stream()
                    .anyMatch(like -> like.getUserId().equals(currentUserId));
        }

        List<CategoryDto> categoryDtos = book.getBookCategories().stream()
                .map(bc -> CategoryDto.builder()
                        .id(bc.getCategory().getId())
                        .name(bc.getCategory().getCategoryName())
                        .build())
                .toList();

        List<TagDto> tagDtos = book.getBookTags().stream()
                .map(bt -> TagDto.builder()
                        .id(bt.getTag().getId())
                        .name(bt.getTag().getTagName())
                        .build())
                .toList();

        List<PublisherDto> publisherDtos = book.getBookPublishers().stream()
                .map(bp -> PublisherDto.builder()
                        .id(bp.getPublisher().getId())
                        .name(bp.getPublisher().getPublisherName())
                        .build())
                .toList();

        List<ReviewDto> previewReviews = book.getReviews().stream()
                .sorted(Comparator.comparing(Review::getCreatedAt).reversed())
                .limit(3)
                .map(ReviewDto::from)
                .toList();

        return BookDetailResponse.builder()
                .id(book.getId()) // Entity 필드명은 id 입니다.
                .isbn(book.getIsbn())
                .title(book.getTitle())
                .volume(book.getVolume())
                .contributorName(contributors)
                .publishDate(book.getPublishDate())
                .publishers(publisherDtos)
                .priceStandard(book.getPriceStandard())
                .priceSales(book.getPriceSales())
                .stockStatus(book.getStockStatus())
                .categories(categoryDtos)
                .tags(tagDtos)
                .isWrapped(book.getIsWrapped())
                .imagePath(thumbnail)
                .chapter(book.getChapter())
                .descriptionHtml(book.getDescription())
                .likeCount((long) book.getLikes().size()) // List 크기로 계산
                .likedByCurrentUser(isLiked)
                .rating(book.getRating()) // 추가된 평점 필드
                .reviewCount((long) book.getReviews().size()) // List 크기로 계산
                .reviews(previewReviews)
                .build();
    }
}
