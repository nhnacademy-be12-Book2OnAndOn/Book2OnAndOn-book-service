package org.nhnacademy.book2onandonbookservice.service.book;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.dto.book.BookDetailResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSaveRequest;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSearchCondition;
import org.nhnacademy.book2onandonbookservice.dto.common.BookContributorDto;
import org.nhnacademy.book2onandonbookservice.dto.common.CategoryDto;
import org.nhnacademy.book2onandonbookservice.dto.common.PublisherDto;
import org.nhnacademy.book2onandonbookservice.dto.common.TagDto;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookCategory;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.nhnacademy.book2onandonbookservice.entity.BookPublisher;
import org.nhnacademy.book2onandonbookservice.entity.BookTag;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.repository.BookLikeRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.CategoryRepository;
import org.nhnacademy.book2onandonbookservice.service.mapper.BookListResponseMapper;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchIndexService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 등록/수정 담당
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class BookServiceImpl implements BookService {

    private final BookFactory bookFactory;
    private final BookRelationService bookRelationService;
    private final BookValidator bookValidator;
    private final BookRepository bookRepository;
    private final BookLikeRepository bookLikeRepository;
    private final CategoryRepository categoryRepository;
    private final BookSearchIndexService bookSearchIndexService;
    private final BookListResponseMapper bookListResponseMapper;

    // 도서 등록
    @Override
    public Long createBook(BookSaveRequest request) {
        bookValidator.validateForCreate(request);
        Book book = bookFactory.createFrom(request);
        bookRelationService.applyRelationsForCreate(book, request);
        Book saved = bookRepository.save(book);

        try {
            bookSearchIndexService.index(saved);
        } catch (Exception e) {
            log.error("ES 인덱싱 실패 - bookId={}", saved.getId(), e);
        }

        return saved.getId();
    }

    // 도서 수정
    @Override
    public void updateBook(Long bookId, BookSaveRequest request) {
        Book book = bookRepository.findByIdWithRelations(bookId)
                .orElseThrow(() -> new IllegalArgumentException("도서를 찾을 수 없습니다. id = " + bookId));

        bookValidator.validateForUpdate(request);   // 수정값 검증
        bookFactory.updateFields(book, request);    // 단일 필드 업데이트
        bookRelationService.applyRelationsForUpdate(book, request); // 연관관계
        bookSearchIndexService.index(book); // 수정 후 인덱스 갱신
    }

    // 도서 삭제
    @Override
    public void deleteBook(Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("도서를 찾을 수 없습니다. id = " + bookId));

        // DB에서 삭제
        bookRepository.delete(book);

        // ES 인덱스에서도 삭제
        bookSearchIndexService.deleteIndex(bookId);
    }

    // 공통 mapper 사용 -> 리스트용 DTO 매핑
    @Override
    @Transactional(readOnly = true)
    public Page<BookListResponse> getBooks(BookSearchCondition condition, Pageable pageable) {
        Page<Book> books = bookRepository.findAll(pageable);
        return books.map(bookListResponseMapper::fromEntity);
    }


    @Override
    @Transactional(readOnly = true)
    public BookDetailResponse getBookDetail(Long bookId, Long currentUserId) {
        Book book = bookRepository.findByIdWithRelations(bookId)
                .orElseThrow(() -> new IllegalArgumentException("도서를 찾을 수 없습니다. id = " + bookId));

        long likeCount = bookLikeRepository.countByBookId(bookId);

        // 비로그인: null, 로그인: true/false
        Boolean likedByCurrentUser = null;
        if (currentUserId != null) {
            likedByCurrentUser = bookLikeRepository.existsByBookIdAndUserId(bookId, currentUserId);
        }

        return toBookDetailResponse(book, likeCount, likedByCurrentUser);
    }

    private BookListResponse toBookListResponse(Book book) {
        // 대표 이미지
        String imagePath = book.getImages().stream()
                .findFirst()
                .map(BookImage::getImagePath)
                .orElse(null);

        // 기여자 이름 리스트
        List<String> contributorNames = book.getBookContributors().stream()
                .map(bc -> bc.getContributor().getContributorName())
                .collect(Collectors.toList());

        // 출판사 이름 리스트
        List<String> publisherNames = book.getBookPublishers().stream()
                .map(bp -> bp.getPublisher().getPublisherName())
                .collect(Collectors.toList());

        // 카테고리 id 리스트 (문자열)
        List<String> categoryIds = book.getBookCategories().stream()
                .map(bc -> String.valueOf(bc.getCategory().getId()))
                .collect(Collectors.toList());

        // 태그 이름 리스트
        List<String> tagNames = book.getBookTags().stream()
                .map(bt -> bt.getTag().getTagName())
                .collect(Collectors.toList());

        return BookListResponse.builder()
                .id(book.getId())
                .title(book.getTitle())
                .volume(book.getVolume())
                .priceStandard(book.getPriceStandard())
                .priceSales(book.getPriceSales())
                .imagePath(imagePath)
                .contributorNames(contributorNames)
                .publisherNames(publisherNames)
                .categoryIds(categoryIds)
                .tagNames(tagNames)
                .build();
    }

    private BookDetailResponse toBookDetailResponse(Book book,
                                                    long likeCount,
                                                    Boolean likedByCurrentUser) {

        // 카테고리 DTO
        List<CategoryDto> categories = book.getBookCategories().stream()
                .map(BookCategory::getCategory)
                .map(category -> CategoryDto.builder()
                        .id(category.getId())
                        .name(category.getCategoryName())
                        .parentId(category.getParent() != null
                                ? category.getParent().getId()
                                : null)
                        .build())
                .collect(Collectors.toList());

        // 태그 DTO
        List<TagDto> tags = book.getBookTags().stream()
                .map(BookTag::getTag)
                .map(tag -> TagDto.builder()
                        .id(tag.getId())
                        .name(tag.getTagName())
                        .build())
                .collect(Collectors.toList());

        // 출판사 DTO
        List<PublisherDto> publishers = book.getBookPublishers().stream()
                .map(BookPublisher::getPublisher)
                .map(publisher -> PublisherDto.builder()
                        .id(publisher.getId())
                        .name(publisher.getPublisherName())
                        .build())
                .collect(Collectors.toList());

        // 기여자 DTO 리스트
        List<BookContributorDto> contributors = book.getBookContributors().stream()
                .map(bc -> BookContributorDto.builder()
                        .id(bc.getId())
                        .roleType(bc.getRoleType())
                        .contributorId(bc.getContributor().getId())
                        .contributorName(bc.getContributor().getContributorName())
                        .build())
                .collect(Collectors.toList());

        // 대표 이미지
        String imagePath = book.getImages().stream()
                .findFirst()
                .map(BookImage::getImagePath)
                .orElse(null);

        // 단일 표시용 기여자 이름(첫 번째)
        String contributorName = contributors.isEmpty()
                ? null
                : contributors.get(0).getContributorName();

        return BookDetailResponse.builder()
                .id(book.getId())
                .isbn(book.getIsbn())
                .title(book.getTitle())
                .volume(book.getVolume())
                .contributorName(contributorName)
                .publishDate(book.getPublishDate())
                .publishers(publishers)
                .priceStandard(book.getPriceStandard())
                .priceSales(book.getPriceSales())
                .stockStatus(book.getStockStatus())
                .categories(categories)
                .tags(tags)
                .isWrapped(book.getIsWrapped())
                .imagePath(imagePath)
                .chapter(book.getChapter())
                .descriptionHtml(book.getDescription())
                .likeCount(likeCount)
                .likedByCurrentUser(likedByCurrentUser)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "categories", unless = "#result == null || #result.isEmpty()")
    public List<CategoryDto> getCategories() {
        List<Category> entities = categoryRepository.findAll();
        List<CategoryDto> allDtos = entities.stream().map(this::CategoryToDto).toList();
        Map<Long, CategoryDto> dtoMap = allDtos.stream()
                .collect(Collectors.toMap(CategoryDto::getId, Function.identity()));

        List<CategoryDto> rootCategories = new ArrayList<>();

        for (CategoryDto dto : allDtos) {
            if (dto.getParentId() == null || dto.getParentId() == 0L) {
                rootCategories.add(dto);
            } else {
                CategoryDto parent = dtoMap.get(dto.getParentId());
                if (parent != null) {
                    parent.getChildren().add(dto);
                }
            }
        }
        return rootCategories;
    }

    //카테고리 생성/수정/삭제 로직이 있을 경우 @CacheEvict(value="categories", allEntries=true)를 붙여줘야함


    ///    내부 로직
    private CategoryDto CategoryToDto(Category category) {
        return CategoryDto.builder()
                .id(category.getId())
                .name(category.getCategoryName())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .build();
    }
}
