package org.nhnacademy.book2onandonbookservice.dto.book;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BookStatusUpdateRequest {

    @NotNull(message = "변경할 상태값은 필수입니다.")
    private BookStatus status;
}
