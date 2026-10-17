package com.yarukov.nosql.config;

import com.yarukov.nosql.model.entity.User;
import com.yarukov.nosql.repository.jpa.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;

    @Override
    public void run(String... args) {
        int addedUsers = 0;

        addedUsers += createUserIfMissing(
                "operator1",
                "Алексей Операторов",
                "+7-999-000-01-01",
                "Главный офис поддержки, комн. 101",
                "OPERATOR");

        addedUsers += createUserIfMissing(
                "operator2",
                "Ольга Сергеева",
                "+7-999-000-02-02",
                "Главный офис поддержки, комн. 102",
                "OPERATOR");

        addedUsers += createUserIfMissing(
                "operator3",
                "Михаил Петров",
                "+7-999-000-03-03",
                "Главный офис поддержки, комн. 103",
                "OPERATOR");

        addedUsers += createUserIfMissing(
                "operator4",
                "Екатерина Лебедева",
                "+7-999-000-04-04",
                "Главный офис поддержки, комн. 104",
                "OPERATOR");

        addedUsers += createUserIfMissing(
                "operator5",
                "Андрей Фёдоров",
                "+7-999-000-05-05",
                "Главный офис поддержки, комн. 105",
                "OPERATOR");

        addedUsers += createUserIfMissing(
                "client_ivan",
                "Иван Кузнецов",
                "+7-999-111-22-33",
                "г. Москва, ул. Ленина, д. 42, кв. 15",
                "CLIENT");

        addedUsers += createUserIfMissing(
                "client_anna",
                "Анна Смирнова",
                "+7-999-222-13-14",
                "г. Москва, ул. Тверская, д. 18, кв. 27",
                "CLIENT");

        addedUsers += createUserIfMissing(
                "client_maxim",
                "Максим Волков",
                "+7-999-333-24-25",
                "г. Москва, Кутузовский проспект, д. 31, кв. 8",
                "CLIENT");

        addedUsers += createUserIfMissing(
                "client_elena",
                "Елена Соколова",
                "+7-999-444-35-36",
                "г. Москва, ул. Арбат, д. 11, кв. 42",
                "CLIENT");

        addedUsers += createUserIfMissing(
                "client_sergey",
                "Сергей Морозов",
                "+7-999-555-46-47",
                "г. Москва, Ленинградский проспект, д. 56, кв. 19",
                "CLIENT");

        addedUsers += createUserIfMissing(
                "client_maria",
                "Мария Новикова",
                "+7-999-666-57-58",
                "г. Москва, ул. Покровка, д. 25, кв. 6",
                "CLIENT");

        addedUsers += createUserIfMissing(
                "courier_dmitry",
                "Дмитрий Быстрый",
                "+7-999-555-44-33",
                "Логистический хаб №3",
                "COURIER");

        addedUsers += createUserIfMissing(
                "courier_oleg",
                "Олег Воронов",
                "+7-999-777-68-69",
                "Логистический хаб №1",
                "COURIER");

        addedUsers += createUserIfMissing(
                "courier_alina",
                "Алина Крылова",
                "+7-999-888-79-80",
                "Логистический хаб №2",
                "COURIER");

        addedUsers += createUserIfMissing(
                "courier_nikolay",
                "Николай Орлов",
                "+7-999-999-81-82",
                "Логистический хаб №4",
                "COURIER");

        if (addedUsers > 0) {
            log.info("В PostgreSQL добавлено тестовых пользователей: {}", addedUsers);
        } else {
            log.info("Все тестовые пользователи уже существуют в PostgreSQL");
        }
    }

    private int createUserIfMissing(String username,
                                    String fullName,
                                    String phone,
                                    String deliveryAddress,
                                    String role) {
        if (userRepository.findByUsername(username).isPresent()) {
            return 0;
        }

        userRepository.save(User.builder()
                .username(username)
                .fullName(fullName)
                .phone(phone)
                .deliveryAddress(deliveryAddress)
                .role(role)
                .build());
        return 1;
    }
}
