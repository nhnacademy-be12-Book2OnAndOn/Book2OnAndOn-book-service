package org.nhnacademy.book2onandonbookservice.event;

public record TagUpdatedEvent(
        Long tagId,
        String oldName,
        String newName
) {
}