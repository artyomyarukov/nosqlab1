package com.yarukov.nosql.controller;

import com.yarukov.nosql.dto.CreateTicketRequest;
import com.yarukov.nosql.dto.TicketAnalyticsResponse;
import com.yarukov.nosql.model.couchdb.TicketConflictException;
import com.yarukov.nosql.model.couchdb.TicketDoc;
import com.yarukov.nosql.model.couchdb.TicketTag;
import com.yarukov.nosql.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @ExceptionHandler(TicketConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(TicketConflictException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", error.getMessage()));
    }

    @PostMapping
    public ResponseEntity<TicketDoc> createTicket(
            @RequestBody CreateTicketRequest request,
            @RequestHeader("X-Session-Token") String token) {
        return ResponseEntity.ok(ticketService.createTicket(request, token));
    }

    @GetMapping
    public ResponseEntity<List<TicketDoc>> getAllTickets() {
        return ResponseEntity.ok(ticketService.getAllTickets());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketDoc> getTicket(@PathVariable String id,
                                              @RequestParam(defaultValue = "false") boolean fresh) {
        return ticketService.getTicket(id, fresh)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<TicketDoc> updateStatus(
            @PathVariable String id,
            @RequestParam String status,
            @RequestParam(required = false) String rev,
            @RequestHeader("X-Session-Token") String token) {
        return ResponseEntity.ok(ticketService.updateTicketStatus(id, status, token, rev));
    }

    @GetMapping("/search")
    public ResponseEntity<List<TicketDoc>> search(
            @RequestParam String tag,
            @RequestParam String fromDate,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int skip) {
        return ResponseEntity.ok(ticketService.findByTags(tag, fromDate, limit, skip));
    }

    @GetMapping("/search/explain")
    public ResponseEntity<JsonNode> explainSearch(
            @RequestParam String tag,
            @RequestParam String fromDate) {
        return ResponseEntity.ok(ticketService.explainSearch(tag, fromDate));
    }

    @GetMapping("/analytics")
    public ResponseEntity<TicketAnalyticsResponse> getAnalytics() {
        return ResponseEntity.ok(ticketService.getAnalytics());
    }



    @PostMapping("/migrate")
    public ResponseEntity<Map<String, String>> runMigration(
            @RequestHeader("X-Session-Token") String token) {
        String result = ticketService.runMigration(token);
        return ResponseEntity.ok(Map.of("message", result));
    }
    @GetMapping("/tags")
    public ResponseEntity<List<TicketTag>> getAvailableTags() {
        return ResponseEntity.ok(List.of(TicketTag.values()));
    }

    @GetMapping("/search/by-attachment")
    public ResponseEntity<List<TicketDoc>> searchByAttachment(
            @RequestParam String contentType) {
        return ResponseEntity.ok(ticketService.findByAttachmentType(contentType));
    }


}