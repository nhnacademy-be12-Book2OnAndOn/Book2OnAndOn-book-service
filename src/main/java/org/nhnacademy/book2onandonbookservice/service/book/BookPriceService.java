package org.nhnacademy.book2onandonbookservice.service.book;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchIndexService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookPriceService {

    private final BookRepository bookRepository;
    private final BookSearchIndexService bookSearchIndexService;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String PRICE_UPDATE_STATUS_KEY = "admin:price:update:status";
    private static final int CHUNK_SIZE = 1000;

    /**
     * 전체 도서 할인율 변경 (비동기 배치)
     */
    @Async
    public void updateGlobalDiscountRate(int discountRate){
        log.info ("전체 도서 할인율 업데이트 시작: {}%", discountRate);

        redisTemplate.opsForValue().set(PRICE_UPDATE_STATUS_KEY, "PROCESSING", 1, TimeUnit.HOURS);

        double multiplier = 1.0 - (discountRate / 100.0);
        int page = 0;

        try{
            while(true){
                boolean hasNext = processPriceUpdateChunk(page, multiplier);

                if(!hasNext) break;
                page++;

                Thread.sleep(50);
            }

            redisTemplate.opsForValue().set(PRICE_UPDATE_STATUS_KEY, "DONE", 1, TimeUnit.DAYS);
            log.info("전체 도서 할인율 업데이트 완료");
        }catch (Exception e){
            log.error("가격 업데이트 중 오류 발생", e);
            redisTemplate.opsForValue().set(PRICE_UPDATE_STATUS_KEY, "FAILED");
        }
    }

    @Transactional
    protected boolean processPriceUpdateChunk(int page, double multiplier){
        Page<Book> books = bookRepository.findAll(PageRequest.of(page, CHUNK_SIZE));

        if(books.isEmpty()){
            return false;
        }

        for(Book book: books){
            long newPrice = (long) (book.getPriceStandard() * multiplier);

            if(book.getPriceSales() == null || book.getPriceSales() != newPrice){
                book.setPriceSales(newPrice);
                bookSearchIndexService.index(book);
            }
        }

        return true;
    }

    public String getUpdateStatus(){
        return redisTemplate.opsForValue().get(PRICE_UPDATE_STATUS_KEY);
    }
}
