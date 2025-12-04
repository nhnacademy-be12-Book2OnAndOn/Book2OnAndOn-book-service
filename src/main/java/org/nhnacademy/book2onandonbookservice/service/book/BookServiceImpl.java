package org.nhnacademy.book2onandonbookservice.service.book;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.client.OrderServiceClient;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.dto.book.BookDetailResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookListResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookOrderResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSaveRequest;
import org.nhnacademy.book2onandonbookservice.dto.book.BookSearchCondition;
import org.nhnacademy.book2onandonbookservice.dto.book.StockRequest;
import org.nhnacademy.book2onandonbookservice.dto.common.CategoryDto;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.exception.NotFoundBookException;
import org.nhnacademy.book2onandonbookservice.exception.OutOfStockException;
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
    private final OrderServiceClient orderServiceClient;
    private final BookHistoryService bookHistoryService;

    // 도서 등록
    @Override
    public Long createBook(BookSaveRequest request) {
        bookValidator.validateForCreate(request);
        Book book = bookFactory.createFrom(request);

        Book saved = bookRepository.save(book);

        bookRelationService.applyRelationsForCreate(saved, request);

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
                .orElseThrow(() -> new NotFoundBookException(bookId));

        bookValidator.validateForUpdate(request);   // 수정값 검증
        bookFactory.updateFields(book, request);    // 단일 필드 업데이트
        bookRelationService.applyRelationsForUpdate(book, request); // 연관관계
        bookSearchIndexService.index(book); // 수정 후 인덱스 갱신
    }

    // 도서 삭제
    @Override
    public void deleteBook(Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new NotFoundBookException(bookId));

        // DB에서 삭제
        bookRepository.delete(book);

        // ES 인덱스에서도 삭제
        bookSearchIndexService.deleteIndex(bookId);
    }

    // 공통 mapper 사용 -> 리스트용 DTO 매핑
    @Override
    @Transactional(readOnly = true)
    public Page<BookListResponse> getBooks(BookSearchCondition condition, Pageable pageable) {
        Page<Book> books = bookRepository.findByStatusNot(BookStatus.BOOK_DELETED, pageable);
        return books.map(bookListResponseMapper::fromEntity);
    }


    @Override
    @Transactional(readOnly = true)
    public BookDetailResponse getBookDetail(Long bookId, Long userId, String guestId) {
        Book book = bookRepository.findByIdWithRelations(bookId)
                .orElseThrow(() -> new NotFoundBookException(bookId));

        if (userId != null) {
            CompletableFuture.runAsync(() -> bookHistoryService.addRecentView(userId, guestId, bookId));
        }
        long likeCount = bookLikeRepository.countByBookId(bookId);

        // 비로그인: null, 로그인: true/false
        Boolean likedByCurrentUser = null;
        if (userId != null) {
            likedByCurrentUser = bookLikeRepository.existsByBookIdAndUserId(bookId, userId);
        }

        return BookDetailResponse.from(book, likeCount, likedByCurrentUser);
    }


    @Override
    @Transactional(readOnly = true)
    //@Cacheable(value = "categories", unless = "#result == null || #result.isEmpty()", cacheManager = "RedisCacheManager")
    public List<CategoryDto> getCategories() {
        List<Category> entities = categoryRepository.findAll();
        List<CategoryDto> allDtos = entities.stream().map(this::CategoryToDto).toList();
        Map<Long, List<CategoryDto>> childrenMap = allDtos.stream()
                .collect(Collectors.groupingBy(dto -> dto.getParentId() != null ? dto.getParentId() : 0L));

        allDtos.forEach(dto -> {
            List<CategoryDto> children = childrenMap.get(dto.getId());
            if (children != null) {
                dto.getChildren().addAll(children);
            }
        });
        return childrenMap.getOrDefault(0L, Collections.emptyList());
    }

    //카테고리 생성/수정/삭제 로직이 있을 경우 @CacheEvict(value="categories", allEntries=true)를 붙여줘야함

    /// 베스트셀러 조회 및 캐싱
    @Cacheable(value = "bestsellers", key = "#period", cacheManager = "RedisCacheManager") //redis
    @Override
    public List<BookListResponse> getBestsellers(String period) {
        List<Long> bookIds = orderServiceClient.getBestSellersBookIds(period);
        //기간별로 받아옵니다 DAILY, WEEK

        if (bookIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Book> books = bookRepository.findAllById(bookIds); //bookId 리스트로 관련된 book 엔티티를 찾습니다.

        Map<Long, Book> bookMap = books.stream()
                .collect(Collectors.toMap(Book::getId,
                        Function.identity())); //Function.identity: 스트림의 요소 그 자체를 값으로 사용하는 것 Book 객체 자체

        return bookIds.stream()
                .filter(bookMap::containsKey)
                .map(bookMap::get)
                .map(BookListResponse::from)
                .toList();
    }

    /// 신간 도서를 출간일 최신순으로 조회하고 캐싱
    @Cacheable(value = "newArrivals", key = "#categoryId + '_' + #pageable.pageNumber", cacheManager = "RedisCacheManager")
    @Override
    public Page<BookListResponse> getNewArrivals(Long categoryId, Pageable pageable) {
        Page<Book> bookPage;

        if (categoryId != null) {
            List<Long> allCategoryIds = getAllCategoryIds(categoryId);
            bookPage = bookRepository.findBooksByCategoryIdsSorted(allCategoryIds, pageable);
        } else {
            bookPage = bookRepository.findAllByOrderByPublishDateDesc(pageable);
        }
        return bookPage.map(BookListResponse::from);
    }

    /// 내부 통신용 주문서 생성 및 결제 검증을 위한 도서 정보 다건 조회
    @Override
    @Transactional(readOnly = true)
    public List<BookOrderResponse> getBooksForOrder(List<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Book> books = bookRepository.findAllById(bookIds);

        return books.stream().map(BookOrderResponse::from).toList();
    }

    /// 재고 감소
    @Override
    @Transactional
    public void decreaseStock(List<StockRequest> requests) {
        requests.sort(Comparator.comparing(StockRequest::getBookId)); //데드락 방지
        for (StockRequest req : requests) {
            int result = bookRepository.decreaseStock(req.getBookId(), req.getQuantity());

            if (result == 0) {
                throw new OutOfStockException("재고가 부족합니다. BookId: " + req.getBookId());
            }

            Book book = bookRepository.findById(req.getBookId())
                    .orElseThrow(() -> new NotFoundBookException(req.getBookId()));

            if (book.getStockCount() <= 0) {
                book.setStatus(BookStatus.SOLD_OUT);
            }

        }
    }

    /// 재고 증가
    @Override
    @Transactional
    public void increaseStock(List<StockRequest> requests) {
        requests.sort(Comparator.comparing(StockRequest::getBookId)); //데드락 방지
        for (StockRequest req : requests) {
            bookRepository.increaseStock(req.getBookId(), req.getQuantity());

            Book book = bookRepository.findById(req.getBookId())
                    .orElseThrow(() -> new NotFoundBookException(req.getBookId()));

            if (book.getStockCount() > 0 && isSoldOut(book.getStatus())) {
                book.setStatus(BookStatus.ON_SALE);
            }
        }
    }

    /// 인기 도서 조회(좋아요순)
    @Override
    @Transactional(readOnly = true)
    public Page<BookListResponse> getPopularBooks(Pageable pageable) {
        Page<Book> bookPage =
                bookRepository.findByStatusOrderByLikeCountDesc(BookStatus.ON_SALE, pageable);

        return bookPage.map(BookListResponse::from);
    }


    /// 도서 상태변경
    @Override
    public void updateBookStatus(Long bookId, BookStatus status) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new NotFoundBookException(bookId));

        book.setStatus(status);

        try {
            bookSearchIndexService.index(book);
        } catch (Exception e) {
            log.error("Es 인덱싱 실패 (상태변경) - bookId={}", bookId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookListResponse> getRecentViews(Long userId, String guestId) {
        List<Long> bookIds = bookHistoryService.getRecentViews(userId, guestId);

        if (bookIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Book> books = bookRepository.findAllById(bookIds);

        Map<Long, Book> bookMap = books.stream()
                .collect(Collectors.toMap(Book::getId, Function.identity()));

        return bookIds.stream()
                .filter(bookMap::containsKey)
                .map(bookMap::get)
                .map(BookListResponse::from)
                .toList();
    }

    @Override
    public void mergeRecentViews(String guestId, Long userId) {
        if (guestId == null || guestId.isBlank() || userId == null) {
            return;
        }
        bookHistoryService.mergeHistory(guestId, userId);
    }

    ///    내부 로직
    private CategoryDto CategoryToDto(Category category) {
        return CategoryDto.builder()
                .id(category.getId())
                .name(category.getCategoryName())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .build();
    }

    private boolean isSoldOut(BookStatus status) {
        return status == BookStatus.SOLD_OUT || status == BookStatus.OUT_OF_STOCK;
    }

    private List<Long> getAllCategoryIds(Long parentId) {
        Category parent = categoryRepository.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("카테고리 없음"));

        List<Long> ids = new ArrayList<>();

        ids.add(parent.getId());

        collectChildIds(parent, ids);
        return ids;
    }

    private void collectChildIds(Category parent, List<Long> ids) {
        if (parent.getChildren() == null || parent.getChildren().isEmpty()) {
            return;
        }
        for (Category child : parent.getChildren()) {
            ids.add(child.getId());
            collectChildIds(child, ids);
        }
    }
}
