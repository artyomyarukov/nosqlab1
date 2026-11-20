package com.yarukov.nosql.model.couchdb;

public class TicketConflictException extends RuntimeException {

    public TicketConflictException() {
        this(null);
    }

    public TicketConflictException(Throwable cause) {
        super("Другой оператор уже изменил тикет. Обновите его и попробуйте снова.", cause);
    }
}
