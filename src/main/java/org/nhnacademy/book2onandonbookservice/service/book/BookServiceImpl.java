package org.nhnacademy.book2onandonbookservice.service.book;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.nhnacademy.book2onandonbookservice.dto.book.BookUpdateRequest;
import org.nhnacademy.book2onandonbookservice.dto.book.CartResponse;
import org.nhnacademy.book2onandonbookservice.dto.book.StockRequest;
import org.nhnacademy.book2onandonbookservice.dto.common.CategoryDto;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.nhnacademy.book2onandonbookservice.entity.Category;
import org.nhnacademy.book2onandonbookservice.exception.NotFoundBookException;
import org.nhnacademy.book2onandonbookservice.exception.NotFoundCategoryException;
import org.nhnacademy.book2onandonbookservice.exception.OutOfStockException;
import org.nhnacademy.book2onandonbookservice.repository.BookLikeRepository;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.repository.CategoryRepository;
import org.nhnacademy.book2onandonbookservice.service.image.ImageUploadService;
import org.nhnacademy.book2onandonbookservice.service.mapper.BookListResponseMapper;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchIndexService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

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
    private final ImageUploadService imageUploadService;
    private final StockService stockService;

    // 도서 등록
    @Override
    @Caching(evict = {
            // 1. 기본 매니저(7일)를 사용하는 'newArrivals' 삭제
            @CacheEvict(value = "newArrivals", allEntries = true, cacheManager = "redisCacheManager"),

            // 2. 전용 매니저(12시간)를 사용하는 'bestsellers' 삭제
            @CacheEvict(value = "bestsellers", allEntries = true, cacheManager = "bestsellersCacheManager")
    })
    public Long createBook(BookSaveRequest request, List<MultipartFile> images) {
        bookValidator.validateForCreate(request);

        // 1. 기본 정보로 엔티티 생성
        Book book = bookFactory.createFrom(request);

        // 2. 이미지 처리 및 썸네일 동기화
        processImagesForCreate(book, images, request.getImageUrl());

        // 3. 저장 (Cascade로 이미지도 같이 저장됨)
        Book saved = bookRepository.save(book);

        // 4. 연관관계 설정 (태그, 작가 등)
        bookRelationService.applyRelationsForCreate(saved, request);

        // 5. 검색 엔진 인덱싱
        try {
            bookSearchIndexService.index(saved);
        } catch (Exception e) {
            log.error("ES 인덱싱 실패 - bookId={}", saved.getId(), e);
        }

        return saved.getId();
    }

    // 도서 수정
    @Override
    @Transactional
    @Caching(evict = {
            // 1. 기본 매니저(7일)를 사용하는 'newArrivals' 삭제
            @CacheEvict(value = "newArrivals", allEntries = true, cacheManager = "redisCacheManager"),
            // 2. 전용 매니저(12시간)를 사용하는 'bestsellers' 삭제
            @CacheEvict(value = "bestsellers", allEntries = true, cacheManager = "bestsellersCacheManager")
    })
    public void updateBook(Long bookId, BookUpdateRequest request, List<MultipartFile> newImages) {
        Book book = bookRepository.findByIdWithRelations(bookId)
                .orElseThrow(() -> new NotFoundBookException(bookId));

        //재고 변경 감지 및 Redis 동기화
        if(request.getStockCount() != null){
            int oldStock = book.getStockCount();
            int newStock = request.getStockCount();

            int diff = newStock - oldStock;

            if(diff != 0){
                book.setStockCount(newStock);

                stockService.increaseStock(bookId, diff);

                if(newStock>0 && book.getStatus() == BookStatus.SOLD_OUT){
                    book.setStatus(BookStatus.ON_SALE);
                }else if(newStock <= 0){
                    book.setStatus(BookStatus.SOLD_OUT);
                }
            }
        }
        // 1. 단순 필드 업데이트
        bookFactory.updateFields(book, request);

        // 2. 이미지 삭제 처리 (삭제된 파일 경로 반환)
        List<String> pathsToDelete = deleteRequestedImages(book, request.getDeleteImageIds());

        // 3. 새 이미지 업로드 및 추가
        uploadNewImages(book, newImages);

        // 4. 썸네일 유효성 검사 및 재설정
        ensureValidThumbnail(book);

        // 5. 연관관계 및 인덱싱 업데이트
        bookRelationService.applyRelationsForUpdate(book, request);
        bookSearchIndexService.index(book);

        // 6. 물리 파일 삭제 (마지막에 수행)
        deletePhysicalFiles(pathsToDelete);
    }

    
    @Override
    @Transactional
    @Caching(evict = {
            // 1. 기본 매니저(7일)를 사용하는 'newArrivals' 삭제
            @CacheEvict(value = "newArrivals", allEntries = true, cacheManager = "redisCacheManager"),

            // 2. 전용 매니저(12시간)를 사용하는 'bestsellers' 삭제
            @CacheEvict(value = "bestsellers", allEntries = true, cacheManager = "bestsellersCacheManager")
    })
    public void updateThumbnail(Long bookId, Long bookImageId) {
        Book book = bookRepository.findByIdWithRelations(bookId)
                .orElseThrow(()-> new NotFoundBookException(bookId));

        Set<BookImage> images = book.getImages();

        // 해당 이미지가 존재하는지 확인
        BookImage targetImage = images.stream()
                .filter(img -> img.getId().equals(bookImageId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("해당 책에 존재하지 않는 이미지 입니다."));

        book.setThumbnail(targetImage.getImagePath());
        // 전체 순회하며 플래그 재설정 (하나만 true, 나머지 false)
        for(BookImage image : images){
            if(image.getId().equals(bookImageId)){
                image.setThumbnail(true);
                // ★ 핵심: Book 엔티티의 문자열 필드도 같이 업데이트
                book.setThumbnail(image.getImagePath());
            } else {
                if(image.isThumbnail()) {
                    image.setThumbnail(false);
                }
            }
        }

        // 변경사항 ES 반영
        bookSearchIndexService.index(book);
    }

    // 도서 삭제
    @Override
    @Transactional
    @Caching(evict = {
            // 1. 기본 매니저(7일)를 사용하는 'newArrivals' 삭제
            @CacheEvict(value = "newArrivals", allEntries = true, cacheManager = "redisCacheManager"),

            // 2. 전용 매니저(12시간)를 사용하는 'bestsellers' 삭제
            @CacheEvict(value = "bestsellers", allEntries = true, cacheManager = "bestsellersCacheManager")
    })
    public void deleteBook(Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new NotFoundBookException(bookId));

        // 삭제할 파일 경로 백업
        List<String> imagePaths = book.getImages().stream()
                .map(BookImage::getImagePath)
                .toList();

        // 1. DB 삭제 시도
        bookRepository.delete(book);

        // 2. 강제 플러시 (DB 제약조건 위반 여부 즉시 확인)
        // 여기서 에러나면 트랜잭션 롤백되고 아래 MinIO/ES 삭제는 실행 안 됨 (안전)
        bookRepository.flush();

        // 3. ES 삭제 (DB 삭제 성공 후)
        try {
            bookSearchIndexService.deleteIndex(bookId);
        } catch (Exception e) {
            log.error("ES 인덱스 삭제 실패 (DB는 삭제됨): bookId={}", bookId, e);
        }

        // 4. MinIO 파일 삭제 (가장 마지막)
        for (String imagePath : imagePaths) {
            try {
                imageUploadService.remove(imagePath);
            } catch (Exception e) {
                log.error("이미지 삭제 실패: path={}", imagePath, e);
            }
        }
    }

    @Override
    public long getBookCount() {
        return bookRepository.count();
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

    /// 베스트셀러 조회 및 캐싱
    @Cacheable(value = "bestsellers", key = "#period", cacheManager = "bestsellersCacheManager") //redis
    @Override
    public List<BookListResponse> getBestsellers(String period) {
        List<Long> bookIds = orderServiceClient.getBestSellersBookIds(period);
        //기간별로 받아옵니다 DAILY, WEEK

        if (bookIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Book> books = bookRepository.findAllById(bookIds); //bookId 리스트로 관련된 book 엔티티를 찾습니다.

        Map<Long, Book> bookMap = books.stream()
                .filter(book -> book.getStatus() != BookStatus.BOOK_DELETED)
                .collect(Collectors.toMap(Book::getId,
                        Function.identity())); //Function.identity: 스트림의 요소 그 자체를 값으로 사용하는 것 Book 객체 자체

        return bookIds.stream()
                .filter(bookMap::containsKey)
                .map(bookMap::get)
                .map(BookListResponse::from)
                .toList();
    }

    @Override
    @Cacheable(
            value = "newArrivals",
            key = "#categoryId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize"
    )
    public Page<BookListResponse> getNewArrivals(Long categoryId, Pageable pageable) {
        long startTime = System.currentTimeMillis();
        log.info(" 신간도서 조회 시작 - categoryId: {}, page: {}, size: {}",
                categoryId, pageable.getPageNumber(), pageable.getPageSize());

        // 1단계: Book + Category + Images 조회
        Page<Book> bookPage = fetchBooks(categoryId, pageable, startTime);
        List<Book> filteredBooks = bookPage.getContent().stream()
                .filter(book -> book.getStatus() != BookStatus.BOOK_DELETED)
                .toList();
        // 2단계: Contributors, Publishers, Tags 조회 (Batch Fetch)
        fetchAdditionalDetails(bookPage.getContent(), startTime);

        // 3단계: DTO 변환
        Page<Book> filteredPage = new PageImpl<>(
                filteredBooks,
                pageable,
                bookPage.getTotalElements() - (bookPage.getContent().size() - filteredBooks.size())
        );

        Page<BookListResponse> result = convertToResponse(filteredPage, startTime);

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("신간도서 조회 완료 - 총 {}ms, {} 건 조회", totalTime, result.getTotalElements());

        return result;
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


    /// 인기 도서 조회(좋아요순)
    @Override
    @Transactional(readOnly = true)
    public Page<BookListResponse> getPopularBooks(Pageable pageable) {
        Page<Book> bookPage =
                bookRepository.findByStatusOrderByLikeCountDesc(BookStatus.ON_SALE, pageable);
        log.info("좋아요 요청 들어옴 갯수: {}", bookPage.getSize());
        return bookPage.map(BookListResponse::from);
    }


    /// 도서 상태변경
    @Override
    @Caching(evict = {
            // 1. 기본 매니저(7일)를 사용하는 'newArrivals' 삭제
            @CacheEvict(value = "newArrivals", allEntries = true, cacheManager = "redisCacheManager"),

            // 2. 전용 매니저(12시간)를 사용하는 'bestsellers' 삭제
            @CacheEvict(value = "bestsellers", allEntries = true, cacheManager = "bestsellersCacheManager")
    })
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

    @Override
    public Map<Long, CartResponse> getBookSnapshots(List<Long> bookIds) {
        List<Book> books = bookRepository.findAllById(bookIds);

        return books.stream()
                .map(CartResponse::from)
                .collect(Collectors.toMap(
                        CartResponse::getBookId,
                        Function.identity()
                ));
    }

    ///    내부 로직

    private boolean isSoldOut(BookStatus status) {
        return status == BookStatus.SOLD_OUT || status == BookStatus.OUT_OF_STOCK;
    }

    private List<Long> getAllCategoryIds(Long categoryId) {
        // 1. 여기서 딱 한 번만 DB 조회!
        Category rootCategory = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundCategoryException(categoryId));

        List<Long> categoryIds = new ArrayList<>();
        // 2. 이후엔 메모리에서 하위 카테고리 싹 긁어모음
        collectSubCategoryIds(rootCategory, categoryIds);

        return categoryIds;
    }
    private void processImagesForCreate(Book book, List<MultipartFile> files, String externalUrl) {
        boolean thumbnailSet = false;

        // 1. 파일 업로드 처리
        if (files != null && !files.isEmpty()) {
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    String url = imageUploadService.uploadBookImage(file);

                    // 첫 번째 이미지를 썸네일로 지정
                    boolean isThumb = !thumbnailSet;

                    BookImage bookImage = BookImage.builder()
                            .book(book)
                            .imagePath(url)
                            .isThumbnail(isThumb)
                            .build();

                    book.getImages().add(bookImage);

                    if (isThumb) {
                        book.setThumbnail(url); // Book 엔티티 동기화
                        thumbnailSet = true;
                    }
                }
            }
        }

        // 2. 파일이 없고 외부 URL만 있는 경우 (알라딘 등)
        if (!thumbnailSet && StringUtils.hasText(externalUrl)) {
            String uploadedUrl = imageUploadService.uploadImageFromUrl(externalUrl);
            String finalUrl = (uploadedUrl != null) ? uploadedUrl : externalUrl;

            BookImage externalImage = BookImage.builder()
                    .book(book)
                    .imagePath(finalUrl)
                    .isThumbnail(true)
                    .build();

            book.getImages().add(externalImage);
            book.setThumbnail(finalUrl); // Book 엔티티 동기화
        }
    }

    private void collectSubCategoryIds(Category category, List<Long> result) {
        result.add(category.getId());
        if (category.getChildren() != null) {
            for (Category child : category.getChildren()) {
                collectSubCategoryIds(child, result);
            }
        }
    }


    private void fetchAdditionalDetails(List<Book> books, long startTime) {
        if (books.isEmpty()) {
            log.debug(" 2단계 스킵 (조회 결과 없음)");
            return;
        }

        List<Long> bookIds = books.stream()
                .map(Book::getId)
                .toList();

        bookRepository.findBooksWithDetails(bookIds);
        log.debug("2단계 쿼리 (상세정보): {}ms", System.currentTimeMillis() - startTime);
    }

    private Page<Book> fetchBooks(Long categoryId, Pageable pageable, long startTime) {
        Page<Book> bookPage;

        if (categoryId != null) {
            // 카테고리 필터링
            List<Long> allCategoryIds = getAllCategoryIds(categoryId);
            log.debug(" 카테고리 ID 수집: {}ms ({} 개)",
                    System.currentTimeMillis() - startTime, allCategoryIds.size());

            long t2 = System.currentTimeMillis();
            bookPage = bookRepository.findBooksByCategoryIdsSorted(allCategoryIds, pageable);
            log.debug("1단계 쿼리 (필터): {}ms ({} 건)",
                    System.currentTimeMillis() - t2, bookPage.getContent().size());
        } else {
            // 전체 조회
            long t2 = System.currentTimeMillis();
            bookPage = bookRepository.findAllByOrderByPublishDateDesc(pageable);
            log.debug(" 1단계 쿼리 (전체): {}ms ({} 건)",
                    System.currentTimeMillis() - t2, bookPage.getContent().size());
        }

        return bookPage;
    }
    private Page<BookListResponse> convertToResponse(Page<Book> bookPage, long startTime) {
        Page<BookListResponse> result = bookPage.map(BookListResponse::from);
        log.debug("DTO 변환: {}ms", System.currentTimeMillis() - startTime);
        return result;
    }


    // 1. 이미지 삭제 로직 추출
    private List<String> deleteRequestedImages(Book book, List<Long> deleteImageIds) {
        if (deleteImageIds == null || deleteImageIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> pathsToDelete = new ArrayList<>();
        Iterator<BookImage> iterator = book.getImages().iterator();

        while (iterator.hasNext()) {
            BookImage img = iterator.next();
            if (deleteImageIds.contains(img.getId())) {
                pathsToDelete.add(img.getImagePath());
                iterator.remove();
            }
        }
        return pathsToDelete;
    }

    // 2. 새 이미지 업로드 로직 추출
    private void uploadNewImages(Book book, List<MultipartFile> newImages) {
        if (newImages == null || newImages.isEmpty()) {
            return;
        }

        for (MultipartFile file : newImages) {
            if (!file.isEmpty()) {
                String url = imageUploadService.uploadBookImage(file);
                book.getImages().add(BookImage.builder()
                        .book(book)
                        .imagePath(url)
                        .isThumbnail(false) // 썸네일 여부는 ensureValidThumbnail에서 최종 결정
                        .build());
            }
        }
    }

    // 3. 썸네일 유효성 보장 로직 추출
    private void ensureValidThumbnail(Book book) {
        boolean thumbnailExists = book.getImages().stream().anyMatch(BookImage::isThumbnail);

        // 썸네일이 없는데 이미지가 남아있다면, 첫 번째 이미지를 썸네일로 승격
        if (!thumbnailExists && !book.getImages().isEmpty()) {
            BookImage newThumb = book.getImages().iterator().next();
            newThumb.setThumbnail(true);
            book.setThumbnail(newThumb.getImagePath());
        } else if (book.getImages().isEmpty()) {
            // 이미지가 아예 없으면 썸네일 제거
            book.setThumbnail(null);
        }
        // 이미 썸네일이 있으면 건드리지 않음
    }

    // 4. 물리 파일 삭제 로직 추출
    private void deletePhysicalFiles(List<String> paths) {
        for (String path : paths) {
            try {
                imageUploadService.remove(path);
            } catch (Exception e) {
                log.warn("이미지 파일 삭제 실패 (DB는 처리됨): {}", path);
            }
        }
    }
}
