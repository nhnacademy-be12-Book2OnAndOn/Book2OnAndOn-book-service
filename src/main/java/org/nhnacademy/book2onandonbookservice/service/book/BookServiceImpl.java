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
import org.springframework.data.domain.Page;
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

    // 도서 등록
    @Override
    @CacheEvict(value={"newArrivals","bestsellers"}, allEntries = true, cacheManager = "RedisCacheManager")
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
    @CacheEvict(value = {"newArrivals", "bestsellers"}, allEntries = true, cacheManager = "RedisCacheManager")
    public void updateBook(Long bookId, BookUpdateRequest request, List<MultipartFile> newImages) {
        Book book = bookRepository.findByIdWithRelations(bookId)
                .orElseThrow(() -> new NotFoundBookException(bookId));

        // 1. 단순 필드 업데이트
        bookFactory.updateFields(book, request);

        // 2. 이미지 삭제 로직 (썸네일 삭제 여부 체크가 핵심)
        List<String> pathsToDelete = new ArrayList<>();
        boolean thumbnailDeleted = false;

        if (request.getDeleteImageIds() != null && !request.getDeleteImageIds().isEmpty()) {
            Iterator<BookImage> iterator = book.getImages().iterator();
            while (iterator.hasNext()) {
                BookImage img = iterator.next();
                if (request.getDeleteImageIds().contains(img.getId())) {
                    // 삭제 대상 수집
                    pathsToDelete.add(img.getImagePath());
                    // 만약 썸네일이 삭제되는 거라면 플래그 ON
                    if (img.isThumbnail()) {
                        thumbnailDeleted = true;
                    }
                    iterator.remove(); // 컬렉션에서 제거 (DB 반영 예정)
                }
            }
        }

        // 3. 새 이미지 추가
        if (newImages != null && !newImages.isEmpty()) {
            for (MultipartFile file : newImages) {
                if (!file.isEmpty()) {
                    String url = imageUploadService.uploadBookImage(file);
                    book.getImages().add(BookImage.builder()
                            .book(book)
                            .imagePath(url)
                            .isThumbnail(false) // 일단 false로 넣고 아래에서 재조정
                            .build());
                }
            }
        }

        // 4. 썸네일 재조정 (삭제됐거나, 원래 없었거나)
        // 로직: 썸네일이 지워졌거나 현재 썸네일 설정이 없다면 -> 남은 이미지 중 하나를 썸네일로 승격
        if (thumbnailDeleted || book.getThumbnail() == null) {
            if (!book.getImages().isEmpty()) {
                // Set이라 순서는 보장 안 되지만 하나를 꺼냄
                BookImage newThumb = book.getImages().iterator().next();
                newThumb.setThumbnail(true);
                book.setThumbnail(newThumb.getImagePath()); // Book 엔티티 동기화
            } else {
                // 이미지가 아예 없으면 썸네일 제거
                book.setThumbnail(null);
            }
        }

        // 5. 연관관계 업데이트
        bookRelationService.applyRelationsForUpdate(book, request);

        // 6. ES 인덱싱
        bookSearchIndexService.index(book);

        // 7. 실제 파일 삭제 (DB 로직이 다 끝나갈 때 쯤 수행)
        for (String path : pathsToDelete) {
            try {
                imageUploadService.remove(path);
            } catch (Exception e) {
                log.warn("이미지 파일 삭제 실패 (DB는 처리됨): {}", path);
            }
        }
    }

    
    @Override
    @Transactional
    @CacheEvict(value = {"newArrivals", "bestsellers"}, allEntries = true, cacheManager = "RedisCacheManager")
    public void updateThumbnail(Long bookId, Long bookImageId) {
        Book book = bookRepository.findByIdWithRelations(bookId)
                .orElseThrow(()-> new NotFoundBookException(bookId));

        Set<BookImage> images = book.getImages();

        // 해당 이미지가 존재하는지 확인
        BookImage targetImage = images.stream()
                .filter(img -> img.getId().equals(bookImageId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("해당 책에 존재하지 않는 이미지 입니다."));

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
    @CacheEvict(value = {"newArrivals", "bestsellers"}, allEntries = true, cacheManager = "RedisCacheManager")
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

    @Override
    public Page<BookListResponse> getBooksByCategory(Long categoryId, Pageable pageable) {
        Category rootCategory = categoryRepository.findById(categoryId)
                .orElseThrow(()-> new NotFoundCategoryException(categoryId));

        List<Long> allCategoryIds = new ArrayList<>();
        collectSubCategoryIds(rootCategory, allCategoryIds);

        Page<Book> books = bookRepository.findByCategory_IdIn(allCategoryIds, pageable);

        return books.map(BookListResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "categoryInfo", key = "#categoryId", cacheManager = "RedisCacheManager")
    public CategoryDto getCategory(Long categoryId){
        Category category = categoryRepository.findById(categoryId).orElseThrow(()-> new NotFoundCategoryException(categoryId)  );

        return CategoryToDto(category);
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
    @Cacheable(value = "categories", unless = "#result == null || #result.isEmpty()", cacheManager = "RedisCacheManager")
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

    @Override
    @Cacheable(
            value = "newArrivals",
            key = "#categoryId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize",
            cacheManager = "RedisCacheManager"
    )
    public Page<BookListResponse> getNewArrivals(Long categoryId, Pageable pageable) {
        long startTime = System.currentTimeMillis();
        log.info(" 신간도서 조회 시작 - categoryId: {}, page: {}, size: {}",
                categoryId, pageable.getPageNumber(), pageable.getPageSize());

        // 1단계: Book + Category + Images 조회
        Page<Book> bookPage = fetchBooks(categoryId, pageable, startTime);

        // 2단계: Contributors, Publishers, Tags 조회 (Batch Fetch)
        fetchAdditionalDetails(bookPage.getContent(), startTime);

        // 3단계: DTO 변환
        Page<BookListResponse> result = convertToResponse(bookPage, startTime);

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
        log.info("좋아요 요청 들어옴 갯수: {}", bookPage.getSize());
        return bookPage.map(BookListResponse::from);
    }


    /// 도서 상태변경
    @Override
    @CacheEvict(value = {"newArrivals", "bestsellers"}, allEntries = true, cacheManager = "RedisCacheManager")
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
            BookImage externalImage = BookImage.builder()
                    .book(book)
                    .imagePath(externalUrl)
                    .isThumbnail(true)
                    .build();

            book.getImages().add(externalImage);
            book.setThumbnail(externalUrl); // Book 엔티티 동기화
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

        long t3 = System.currentTimeMillis();
        List<Long> bookIds = books.stream()
                .map(Book::getId)
                .toList();

        bookRepository.findBooksWithDetails(bookIds);
        log.debug("2단계 쿼리 (상세정보): {}ms", System.currentTimeMillis() - t3);
    }

    private Page<Book> fetchBooks(Long categoryId, Pageable pageable, long startTime) {
        Page<Book> bookPage;

        if (categoryId != null) {
            // 카테고리 필터링
            long t1 = System.currentTimeMillis();
            List<Long> allCategoryIds = getAllCategoryIds(categoryId);
            log.debug(" 카테고리 ID 수집: {}ms ({} 개)",
                    System.currentTimeMillis() - t1, allCategoryIds.size());

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
        long t4 = System.currentTimeMillis();
        Page<BookListResponse> result = bookPage.map(BookListResponse::from);
        log.debug("DTO 변환: {}ms", System.currentTimeMillis() - t4);
        return result;
    }
    private void processImages(Book book, List<MultipartFile> images) {
        if (images == null || images.isEmpty()) return;

        boolean hasThumbnail = book.getImages().stream().anyMatch(BookImage::isThumbnail);

        for (MultipartFile file : images) {
            if (!file.isEmpty()) {
                String minioUrl = imageUploadService.uploadBookImage(file);

                // 썸네일이 없으면 현재 이미지를 썸네일로 지정
                boolean isThisThumbnail = !hasThumbnail;
                if (isThisThumbnail) hasThumbnail = true;

                book.getImages().add(BookImage.builder()
                        .book(book)
                        .imagePath(minioUrl)
                        .isThumbnail(isThisThumbnail)
                        .build());
            }
        }
    }
}
