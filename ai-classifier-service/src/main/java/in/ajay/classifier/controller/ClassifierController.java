package in.ajay.classifier.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import in.ajay.classifier.dto.ClassifyRequest;
import in.ajay.classifier.dto.TicketClassification;
import in.ajay.classifier.service.TicketClassifierService;
import jakarta.validation.Valid;

@RestController 
@RequestMapping("/api/classify")
public class ClassifierController {

    private final TicketClassifierService ticketClassifierService;

    public ClassifierController(TicketClassifierService ticketClassifierService){
        this.ticketClassifierService = ticketClassifierService;
    }

    @PostMapping 
    public ResponseEntity<TicketClassification> classify(
        @RequestBody @Valid ClassifyRequest request
    ){
        return 
        ResponseEntity.ok(ticketClassifierService.classify(request));
    }

}
