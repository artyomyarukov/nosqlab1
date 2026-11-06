package com.yarukov.nosql.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yarukov.nosql.dao.couchdb.CouchDbSystemDao;
import com.yarukov.nosql.dao.couchdb.TicketCrudDao;
import com.yarukov.nosql.dao.couchdb.TicketQueryDao;
import com.yarukov.nosql.model.couchdb.TicketDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketCrudDao ticketCrudDao;
    private final TicketQueryDao ticketQueryDao;
    private final CouchDbSystemDao systemDao;
    private final AuthService authService;


    public TicketDoc createTicket(TicketDoc ticket, String token) {
        authService.getActiveSessionOrThrow(token);

        ticket.setId("ticket_" + Instant.now().toEpochMilli());
        ticket.setStatus("NEW");
        ticket.setChangeCount(1);
        ticket.setCreatedAt(Instant.now());

        return ticketCrudDao.save(ticket);
    }

    public TicketDoc updateTicketStatus(String ticketId, String newStatus, String token) {
        authService.getActiveSessionOrThrow(token);

        TicketDoc ticket = ticketCrudDao.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Тикет не найден"));

        ticket.setStatus(newStatus);
        ticket.setChangeCount(ticket.getChangeCount() + 1);

        return ticketCrudDao.save(ticket);
    }

    public Optional<TicketDoc> getTicket(String id) {
        return ticketCrudDao.findById(id);
    }

    public List<TicketDoc> findByTags(String tag, String fromDate, int limit, int skip) {
        return ticketQueryDao.findByTagsAndDate(tag, fromDate, limit, skip);
    }


    public JsonNode explainSearch(String tag, String fromDate) {
        return ticketQueryDao.explainFindByTagsAndDate(tag, fromDate);
    }

    public JsonNode getAnalytics() {
        return ticketQueryDao.getAverageChangesByStatus();
    }



    public String runMigration(String token) {
        authService.getActiveSessionOrThrow(token);

        List<TicketDoc> allTickets = ticketCrudDao.findAll();
        int migratedCount = 0;

        for (TicketDoc ticket : allTickets) {
            if (ticket.getEmbeddedUser() != null && ticket.getUserId() == null) {
                ticket.setUserId(ticket.getEmbeddedUser().getId());
                ticket.setEmbeddedUser(null);
                ticketCrudDao.save(ticket);
                migratedCount++;
            }
        }

        return "Миграция завершена. Обновлено документов (v1 -> v2): " + migratedCount;
    }
}