package in.ajay.ticketservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import in.ajay.ticketservice.entity.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    
}
