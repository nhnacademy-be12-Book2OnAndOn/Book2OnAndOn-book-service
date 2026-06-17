package org.nhnacademy.book2onandonbookservice.repository;

import org.nhnacademy.book2onandonbookservice.entity.BookStockReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookStockReservationRepository extends JpaRepository<BookStockReservation, Long> {
    List<BookStockReservation> findByOrderNumberAndStatus(String orderNumber, BookStockReservation.ReservationStatus status);
}
