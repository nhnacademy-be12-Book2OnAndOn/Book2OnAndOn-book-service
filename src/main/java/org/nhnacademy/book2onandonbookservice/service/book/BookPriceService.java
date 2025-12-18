package org.nhnacademy.book2onandonbookservice.service.book;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.repository.BookRepository;
import org.nhnacademy.book2onandonbookservice.service.search.BookSearchIndexService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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
    private BookPriceService self;


    /*
    Spring의 @Transactional이 적용된 메서드를 같은 클래스 내부에서 this로 호출하면 트랜잭션이 동작하지 않는 문제 발생
    Spring의 트랜잭션은 Proxy 라는 가짜 객체를 통해 동작함
    외부에서 호출: 프록시가 요청을 가로채서 트랜잭션을 시작하고 -> 실제 메서드 실행
    내부에서 호출: 프록시를 거치지않고 자기 자신의 메서드를 바로 호출 -> 트랜잭션 작동 XX

    따라서 자기자신을 주입받아서, 그 주입받은 객체(프록시)를 통해 호출한다. 여기서 순환 참조 문제를 막기 위해 @Lazy를 사용해야함
    */

    @Autowired
    public void setSelf(@Lazy BookPriceService self) {
        this.self = self;
    }


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
//                boolean hasNext = processPriceUpdateChunk(page, multiplier);  원래 이런식으로 받았는데
                boolean hasNext = self.processPriceUpdateChunk(page, multiplier); //이런식으로 Lazy를 건 서비스 호출필요 -> 이렇게 되면 메서드 호출이 프록시를 통과하게 돼서 트랜잭션이 정상적으로 걸리게됨
                //근데 사실 저 윗줄 처럼 하려면 processPriceUpdateChunk에 트랜잭션 어노테이션을 빼면됨

                if(!hasNext) break;
                page++;

                Thread.sleep(50);
            }

            redisTemplate.opsForValue().set(PRICE_UPDATE_STATUS_KEY, "DONE", 1, TimeUnit.DAYS);
            log.info("전체 도서 할인율 업데이트 완료");
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("가격 업데이트 작업이 중단되었습니다.");
        } catch (Exception e) {
            log.error("가격 업데이트 중 오류 발생", e);
            redisTemplate.opsForValue().set(PRICE_UPDATE_STATUS_KEY, "FAILED");
        }
    }

    @Transactional
    public boolean processPriceUpdateChunk(int page, double multiplier){
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
