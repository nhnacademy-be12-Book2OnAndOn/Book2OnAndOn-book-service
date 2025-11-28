package org.nhnacademy.book2onandonbookservice.service.book;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
import org.nhnacademy.book2onandonbookservice.dto.book.StockDecreaseRequest;
import org.nhnacademy.book2onandonbookservice.dto.common.CategoryDto;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
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
        Page<Book> books = bookRepository.findAll(pageable);
        return books.map(bookListResponseMapper::fromEntity);
    }


    @Override
    @Transactional(readOnly = true)
    public BookDetailResponse getBookDetail(Long bookId, Long currentUserId) {
        Book book = bookRepository.findByIdWithRelations(bookId)
                .orElseThrow(() -> new NotFoundBookException(bookId));

        long likeCount = bookLikeRepository.countByBookId(bookId);

        // 비로그인: null, 로그인: true/false
        Boolean likedByCurrentUser = null;
        if (currentUserId != null) {
            likedByCurrentUser = bookLikeRepository.existsByBookIdAndUserId(bookId, currentUserId);
        }

        return BookDetailResponse.from(book, likeCount, likedByCurrentUser);
    }


    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "categories", unless = "#result == null || #result.isEmpty()", cacheManager = "RedisCacheManager")
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
                .collect(Collectors.toList());
    }

    /// 신간 도서를 출간일 최신순으로 조회하고 캐싱
    @Cacheable(value = "newArrivals", key = "#categoryId + '_' + #pageable.pageNumber", cacheManager = "RedisCacheManager")
    @Override
    public Page<BookListResponse> getNewArrivals(Long categoryId, Pageable pageable) {
        Page<Book> bookPage;

        if (categoryId != null) {
            bookPage = bookRepository.findNewArrivalsByCategoryId(categoryId, pageable);
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

        return books.stream().map(BookOrderResponse::from).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void decreaseStock(List<StockDecreaseRequest> requests) {
        for (StockDecreaseRequest req : requests) {
            int result = bookRepository.decreaseStock(req.getBookId(), req.getQuantity());

            if (result == 0) {
                throw new OutOfStockException("재고가 부족합니다. BookId: " + req.getBookId());
            }

            Book book = bookRepository.findById(req.getBookId())
                    .orElseThrow(() -> new NotFoundBookException(req.getBookId()));

            if (book.getStockCount() <= 0) {
                book.setStockStatus(BookStatus.SOLD_OUT.toString());
            }

            try {
                bookSearchIndexService.index(book);
            } catch (Exception e) {
                //ES 갱신 실패가 결제 로직 전체를 롤백시켜야하나?
                //보통 로그를 남기고, 별도의 재시도 큐(RabbitMQ)에 넣거나 넘어간다.
                /*
                TODO
                DB 트랜잭션이 커밋된 직후 bookSearchIndexService.index(book)를 호출할 때
                네트워크 문제나, ES 서버 다운/부하(엘라스틱 서치가 (Garage Collection)중이라 멈췄거나, 디스크가
                꽉 찼거나, 너무 바빠서 타임아웃될때 )
                DB는 품절이 됐는데 ES에서 갱신이 안돼서 홈페이지에선 판매중인데 실제 DB에선 품절인 경우가 돼버림
                그럼 어떻게 해야되나? (방법)
                1. 로그만 남기기
                2. 스프링 Retry 적용 (재시도 횟수를 정할 수 있음 이러면 일시적인 네트워크문제는 해결 근데 다른 오류에 대응 부족)
                3. 스케줄러로 해결 (인덱싱 에러에 대한 DB테이블을 만들어서 스케줄러로 5분마다 해당 테이블 읽어서 재색인 시도)
                4. 메시지 큐 (RabbitMQ)
                   - decreaseStock메서드에서는 DB 업데이트만 하고 이벤트를 MQ에 이벤트(BookId 변경 감지)를 MQ에 던지고 끝냄
                   - 별도의 Consumer 서버(큐에서 메시지를 빼오는 애)가 MQ에서 메시지를 꺼내서 bookSearchIndexService.index(book)
                   - 근데 재고 처리 로직에만 도입하는게 아닌 엘라스틱 서치 자체를 인덱싱이 수행될 때 RabbitMQ에 먼저 메시지를 던져서 뒷단에서
                   수행되게 한다면? 지금처럼 order-service와 연동해서 로직을 처리할때 다른 서비스들을 방해하지않을 수 있음
                   - 내 생각에는... 위의 이유가 관건이라면 재고량만 RabbitMQ로 관리하면 좋은데 코드의 일관성이나 Book-Service가 돌아갈 때
                   사소한 Book 인덱싱까지도 언제 어떻게 에러가 터질지 모르니깐 댐 역할로 RabbitMq로 막는게 좋다고 생각은 하는데
                   - 파싱같은 대용량은 벌크API로 하는게 나을거 같긴함
                   - 정리하자면
                   파싱 관련 인덱싱 (엘라스틱서치 Bulk API)
                   사소한 인덱싱 및 재고처리로직 (RabbitMQ를 통한 인덱싱)
                 */
                log.error("ES 재고 동기화 실패 - bookId={}", book.getId(), e);
            }
        }
    }

    ///    내부 로직
    private CategoryDto CategoryToDto(Category category) {
        return CategoryDto.builder()
                .id(category.getId())
                .name(category.getCategoryName())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .build();
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
}
