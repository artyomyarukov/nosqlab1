package com.yarukov.nosql.model.couchdb;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TicketTag {
    COLD_FOOD("cold_food"),
    LATE_DELIVERY("late_delivery"),
    URGENT("urgent"),
    REFUND("refund"),
    COMPLAINT("complaint"),
    CLIENT_ISSUE("client_issue"),
    MISSING_ITEM("missing_item");

    private final String value;

    TicketTag(String value) {
        this.value = value;
    }


    @JsonValue
    public String getValue() {
        return value;
    }
    
    @JsonCreator
    public static TicketTag fromValue(String value) {
        for (TicketTag tag : values()) {
            if (tag.value.equalsIgnoreCase(value) || tag.name().equalsIgnoreCase(value)) {
                return tag;
            }
        }
        throw new IllegalArgumentException("Недопустимая метка обращения: " + value);
    }
}