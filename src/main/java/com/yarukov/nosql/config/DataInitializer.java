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
        if (userRepository.count() == 0) {
            userRepository.save(User.builder()
                    .username("operator1")
                    .fullName("Алексей Операторов")
                    .phone("+7-999-000-01-01")
                    .deliveryAddress("Главный офис поддержки, комн. 101")
                    .role("OPERATOR")
                    .build());

            userRepository.save(User.builder()
                    .username("client_ivan")
                    .fullName("Иван Кузнецов")
                    .phone("+7-999-111-22-33")
                    .deliveryAddress("г. Москва, ул. Ленина, д. 42, кв. 15")
                    .role("CLIENT")
                    .build());

            userRepository.save(User.builder()
                    .username("courier_dmitry")
                    .fullName("Дмитрий Быстрый")
                    .phone("+7-999-555-44-33")
                    .deliveryAddress("Логистический хаб №3")
                    .role("COURIER")
                    .build());

            log.info("Тестовые пользователи успешно добавлены в PostgreSQL");
        }
    }
}