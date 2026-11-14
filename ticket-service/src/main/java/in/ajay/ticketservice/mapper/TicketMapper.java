package in.ajay.ticketservice.mapper;
import org.mapstruct.Mapper;

import in.ajay.ticketservice.dto.TicketResponse;
import in.ajay.ticketservice.entity.Ticket;

@Mapper(componentModel = "spring")
public interface TicketMapper {

    TicketResponse toResponse(Ticket ticket);
}
