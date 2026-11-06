package com.yarukov.nosql.controller;

import com.yarukov.nosql.model.couchdb.TicketDoc;
import com.yarukov.nosql.model.couchdb.TicketTag;
import com.yarukov.nosql.service.TicketService;
import lombok.RequiredArgsConstructor;
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

    @PostMapping
    public ResponseEntity<TicketDoc> createTicket(
            @RequestBody TicketDoc ticket,
            @RequestHeader("X-Session-Token") String token) {
        return ResponseEntity.ok(ticketService.createTicket(ticket, token));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketDoc> getTicket(@PathVariable String id) {
        return ticketService.getTicket(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<TicketDoc> updateStatus(
            @PathVariable String id,
            @RequestParam String status,
            @RequestHeader("X-Session-Token") String token) {
        return ResponseEntity.ok(ticketService.updateTicketStatus(id, status, token));
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
    public ResponseEntity<JsonNode> getAnalytics() {
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
}