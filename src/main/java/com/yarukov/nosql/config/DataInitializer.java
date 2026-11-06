package com.yarukov.nosql.config;

import com.yarukov.nosql.dao.couchdb.TicketCrudDao;
import com.yarukov.nosql.dao.couchdb.UserCouchDao;
import com.yarukov.nosql.model.couchdb.AttachmentMeta;
import com.yarukov.nosql.model.couchdb.TicketDoc;
import com.yarukov.nosql.model.couchdb.UserDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserCouchDao userCouchDao;
    private final TicketCrudDao ticketCrudDao;

    @Override
    public void run(String... args) {
        log.info("Начинаем проверку и инициализацию тестовых данных в CouchDB...");

        int addedUsers = 0;

        addedUsers += createUserIfMissing("operator1", "Алексей Операторов", "+7-999-000-01-01", "Главный офис, комн. 101", "OPERATOR");
        addedUsers += createUserIfMissing("operator2", "Ольга Сергеева", "+7-999-000-02-02", "Главный офис, комн. 102", "OPERATOR");
        addedUsers += createUserIfMissing("operator3", "Михаил Петров", "+7-999-000-03-03", "Главный офис, комн. 103", "OPERATOR");
        UserDoc ivan = createUserDocIfMissing("client_ivan", "Иван Кузнецов", "+7-999-111-22-33", "г. Москва, ул. Ленина, д. 42", "CLIENT");
        UserDoc anna = createUserDocIfMissing("client_anna", "Анна Смирнова", "+7-999-222-13-14", "г. Москва, ул. Тверская, д. 18", "CLIENT");
        UserDoc maxim = createUserDocIfMissing("client_maxim", "Максим Волков", "+7-999-333-24-25", "г. Москва, Кутузовский пр., д. 31", "CLIENT");
        addedUsers += createUserIfMissing("client_elena", "Елена Соколова", "+7-999-444-35-36", "г. Москва, ул. Арбат, д. 11", "CLIENT");
        addedUsers += createUserIfMissing("client_sergey", "Сергей Морозов", "+7-999-555-46-47", "г. Москва, Ленинградский пр., д. 56", "CLIENT");
        addedUsers += createUserIfMissing("client_maria", "Мария Новикова", "+7-999-666-57-58", "г. Москва, ул. Покровка, д. 25", "CLIENT");

        addedUsers += createUserIfMissing("courier_dmitry", "Дмитрий Быстрый", "+7-999-555-44-33", "Логистический хаб №3", "COURIER");
        UserDoc oleg = createUserDocIfMissing("courier_oleg", "Олег Воронов", "+7-999-777-68-69", "Логистический хаб №1", "COURIER");
        addedUsers += createUserIfMissing("courier_alina", "Алина Крылова", "+7-999-888-79-80", "Логистический хаб №2", "COURIER");
        addedUsers += createUserIfMissing("courier_nikolay", "Николай Орлов", "+7-999-999-81-82", "Логистический хаб №4", "COURIER");

        if (addedUsers > 0) {
            log.info("В CouchDB добавлено новых пользователей: {}", addedUsers);
        }

        if (ticketCrudDao.findAll().isEmpty()) {
            createTestTickets(ivan, anna, maxim, oleg);
            log.info("В CouchDB добавлены тестовые обращения в поддержку (Tickets)!");
        }
    }

    private int createUserIfMissing(String username, String fullName, String phone, String deliveryAddress, String role) {
        if (userCouchDao.findByUsername(username).isPresent()) {
            return 0;
        }
        createUser(username, fullName, phone, deliveryAddress, role);
        return 1;
    }

    private UserDoc createUserDocIfMissing(String username, String fullName, String phone, String deliveryAddress, String role) {
        return userCouchDao.findByUsername(username)
                .orElseGet(() -> createUser(username, fullName, phone, deliveryAddress, role));
    }

    private UserDoc createUser(String username, String fullName, String phone, String deliveryAddress, String role) {
        return userCouchDao.save(UserDoc.builder()
                .id("user_" + UUID.randomUUID().toString().substring(0, 8))
                .username(username)
                .fullName(fullName)
                .phone(phone)
                .deliveryAddress(deliveryAddress)
                .role(role)
                .createdAt(Instant.now())
                .build());
    }

    private void createTestTickets(UserDoc ivan, UserDoc anna, UserDoc maxim, UserDoc oleg) {
        if (ivan != null) {
            ticketCrudDao.save(TicketDoc.builder()
                    .id("ticket_" + UUID.randomUUID().toString().substring(0, 8))
                    .title("Холодная пицца")
                    .description("Курьер привез ледяную пиццу, прошу вернуть деньги.")
                    .status("IN_PROGRESS")
                    .changeCount(2)
                    .embeddedUser(ivan)
                    .tags(List.of("cold_food", "refund"))
                    .createdAt(Instant.now().minusSeconds(86400))
                    .attachments(List.of(
                            AttachmentMeta.builder()
                                    .fileName("photo_pizza.jpg")
                                    .contentType("image/jpeg")
                                    .fileSize(350400L)
                                    .uploadedAt(Instant.now())
                                    .build()
                    ))
                    .build());
        }


        if (anna != null) {
            ticketCrudDao.save(TicketDoc.builder()
                    .id("ticket_" + UUID.randomUUID().toString().substring(0, 8))
                    .title("Опоздал курьер")
                    .description("Ждала заказ 2 часа!")
                    .status("RESOLVED")
                    .changeCount(4)
                    .userId(anna.getId())
                    .tags(List.of("late_delivery", "complaint"))
                    .createdAt(Instant.now().minusSeconds(3600))
                    .build());
        }


        if (oleg != null) {
            ticketCrudDao.save(TicketDoc.builder()
                    .id("ticket_" + UUID.randomUUID().toString().substring(0, 8))
                    .title("Клиент не выходит на связь")
                    .description("Стою у подъезда 15 минут, телефон недоступен.")
                    .status("NEW")
                    .changeCount(1)
                    .userId(oleg.getId())
                    .tags(List.of("client_issue", "urgent"))
                    .createdAt(Instant.now())
                    .build());
        }

        if (maxim != null) {
            ticketCrudDao.save(TicketDoc.builder()
                    .id("ticket_" + UUID.randomUUID().toString().substring(0, 8))
                    .title("Не положили соус")
                    .description("Забыл положить сырный соус в пакет.")
                    .status("CLOSED")
                    .changeCount(3)
                    .userId(maxim.getId())
                    .tags(List.of("missing_item"))
                    .createdAt(Instant.now().minusSeconds(172800))
                    .build());
        }
    }
}