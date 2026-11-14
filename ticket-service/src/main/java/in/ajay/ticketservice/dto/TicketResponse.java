package in.ajay.ticketservice.dto;

import java.time.Instant;

import in.ajay.ticketservice.entity.Ticket;
import in.ajay.ticketservice.entity.TicketStatus;

public record TicketResponse(
    Long id,
    String subject,
    String description,
    String requesterEmail,
    TicketStatus status,
    String priority,
    String sentiment,
    String intent,
    String suggestedTeam,
    Instant createdAt,
    Instant updatedAt
) {
    public static TicketResponse from(Ticket ticket){
        return new TicketResponse(ticket.getId(),
        ticket.getSubject(), ticket.getDescription(), ticket.getRequesterEmail(),
        ticket.getStatus(), ticket.getPriority(), ticket.getSentiment(),
        ticket.getIntent(), ticket.getSuggestedTeam(),ticket.getCreatedAt(),ticket.getUpdatedAt());
    }
}

