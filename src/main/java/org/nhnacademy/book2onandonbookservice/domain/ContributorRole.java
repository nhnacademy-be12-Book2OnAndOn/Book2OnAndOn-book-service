package org.nhnacademy.book2onandonbookservice.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ContributorRole {
    AUTHOR("AUTHOR", "지은이"),
    TRANSLATOR("TRANSLATOR", "옮긴이"),
    ILLUSTRATOR("ILLUSTRATOR", "그림"),
    EDITOR("EDITOR", "엮은이"),
    COMPILER("COMPILER", "편");

    private final String code;
    private final String koreanName;

    public static ContributorRole fromKorean(String koreanName) {
        for (ContributorRole role : values()) {
            if (role.koreanName.equals(koreanName)) {
                return role;
            }
        }
        return AUTHOR; // 기본값
    }
}
