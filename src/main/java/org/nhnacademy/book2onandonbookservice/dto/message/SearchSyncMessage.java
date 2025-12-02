package org.nhnacademy.book2onandonbookservice.dto.message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class SearchSyncMessage {
    private Long targetId;
    private SyncType type;

    public enum SyncType {
        CATEGORY, TAG
    }
}
