package org.nhnacademy.book2onandonbookservice.service.review;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.client.OrderServiceClient;
import org.nhnacademy.book2onandonbookservice.exception.PurchaseVerificationUnavailableException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseVerificationService {

    private final OrderServiceClient orderServiceClient;

    /**
     * 구매 이력 검증 메서드
     * 1. Redis("purchaseHistory")
     * 2. 없으면 OrderServiceClient 호출 (Feign Client)
     * 3. 결과가 true일때만 Redis에 캐싱 (false는 캐싱 안함)
     */

    @Cacheable(
            value = "purchaseHistory",
            key="#userId + ':' + #bookId",
            cacheManager = "purchaseHistoryCacheManager",
            unless = "#result == false"
    )
    public boolean verifyPurchase(Long userId, Long bookId){
        log.info("Order Service API 호출 (Redis Miss) -> 구매 확인 시도: userId={}, bookId={}", userId, bookId);
        try{
            return orderServiceClient.hasPurchased(userId, bookId);
        }catch (Exception e){
            log.error("Order Service 호출 실패: {}", e.getMessage());
            throw new PurchaseVerificationUnavailableException("현재 시스템 점검 중으로 구매 이력을 확인할 수 없습니다.");
        }
    }
}
