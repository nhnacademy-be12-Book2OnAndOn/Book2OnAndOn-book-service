package org.nhnacademy.book2onandonbookservice.domain;

public enum BookStatus {
    OUT_OF_STOCK,   //품절(입고 예정 없음) 북 리스트에서 비활성화 처리
    SOLD_OUT,   //일시 품절(재입고 예정) 북 디테일까진 들어가지는데 구매하기 버튼 비활성화 처리
    BOOK_DELETED,   //삭제, 노출 중단 아예안보임
    ON_SALE //판매중
    ;


    @Override
    public String toString() {
        return super.toString();
    }
}
