package org.nhnacademy.book2onandonbookservice.domain;

public enum BookStatus {
    OUT_OF_STOCK,   //품절(입고 예정 없음)
    SOLD_OUT,   //일시 품절(재입고 예정)
    BOOK_DELETED,   //삭제, 노출 중단
    ON_SALE //판매중
    ;


    @Override
    public String toString() {
        return super.toString();
    }
}
