package org.nhnacademy.book2onandonbookservice.exception;

//[403]
public class BookNotPurchasedException extends RuntimeException {
    public BookNotPurchasedException() {
        super("해당 도서를 구매후 배송이 완료된 회원만 리뷰 작성 가능합니다.");
    }
}
