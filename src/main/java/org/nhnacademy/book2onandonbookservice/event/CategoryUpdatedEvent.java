package org.nhnacademy.book2onandonbookservice.event;

public record CategoryUpdatedEvent(
        Long categoryId,
        String oldName,
        String newName
) {
}
