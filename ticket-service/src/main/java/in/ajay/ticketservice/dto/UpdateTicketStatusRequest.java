package in.ajay.ticketservice.dto;

import in.ajay.ticketservice.entity.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTicketStatusRequest (

    @NotNull(message = "Status must not be null")
    TicketStatus status
){}
