package org.nhnacademy.book2onandonbookservice.domain;

public enum EnrichmentStatus {
    PENDING, //대기중
    DONE, //성공
    FAILED, //실패
    NOT_FOUND, //알라딘이 ISBN을 찾을 수 없을때
    PROCESSING
}
