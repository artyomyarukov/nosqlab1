package com.yarukov.nosql.service;

import com.yarukov.nosql.dao.couchdb.TicketCrudDao;
import com.yarukov.nosql.dao.couchdb.TicketQueryDao;
import com.yarukov.nosql.dao.couchdb.UserCouchDao;
import com.yarukov.nosql.model.couchdb.TicketDoc;
import com.yarukov.nosql.model.couchdb.UserDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketCrudDao ticketCrudDao;
    private final TicketQueryDao ticketQueryDao;
    private final UserCouchDao userCouchDao;
    private final AuthService authService;

    private static final Set<String> STATUSES = Set.of("NEW", "IN_PROGRESS", "RESOLVED", "CLOSED");


    public TicketDoc createTicket(TicketDoc ticket, String token) {
        authService.getActiveSessionOrThrow(token);

        if (ticket.getUserId() == null || ticket.getUserId().isBlank()) {
            throw new RuntimeException("У обращения должен быть указан пользователь");
        }
        userCouchDao.findById(ticket.getUserId())
                .orElseThrow(() -> new RuntimeException("Пользователь обращения не найден в CouchDB"));

        ticket.setId("ticket_" + UUID.randomUUID());
        ticket.setRev(null);
        ticket.setEmbeddedUser(null);
        ticket.setStatus("NEW");
        ticket.setChangeCount(1);
        ticket.setCreatedAt(Instant.now());

        return ticketCrudDao.save(ticket);
    }

    public TicketDoc updateTicketStatus(String ticketId, String newStatus, String token) {
        authService.getActiveSessionOrThrow(token);

        if (newStatus == null || !STATUSES.contains(newStatus)) {
            throw new RuntimeException("Недопустимый статус обращения");
        }

        TicketDoc ticket = ticketCrudDao.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Тикет не найден"));

        if (newStatus.equals(ticket.getStatus())) {
            return ticket;
        }
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
                UserDoc embedded = ticket.getEmbeddedUser();
                if (embedded.getId() == null || embedded.getId().isBlank()) {
                    throw new RuntimeException("У вложенного пользователя отсутствует ID");
                }
                if (userCouchDao.findById(embedded.getId()).isEmpty()) {
                    UserDoc user = UserDoc.builder()
                            .id(embedded.getId())
                            .username(embedded.getUsername())
                            .fullName(embedded.getFullName())
                            .phone(embedded.getPhone())
                            .deliveryAddress(embedded.getDeliveryAddress())
                            .role(embedded.getRole())
                            .createdAt(embedded.getCreatedAt())
                            .build();
                    userCouchDao.save(user);
                }
                ticket.setUserId(embedded.getId());
                ticket.setEmbeddedUser(null);
                ticketCrudDao.save(ticket);
                migratedCount++;
            }
        }

        return "Миграция завершена. Обновлено документов (v1 -> v2): " + migratedCount;
    }
}