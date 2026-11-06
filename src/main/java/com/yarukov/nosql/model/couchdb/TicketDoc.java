package com.yarukov.nosql.model.couchdb;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TicketDoc {

    @JsonProperty("_id")
    private String id;

    @JsonProperty("_rev")
    private String rev;

    private String title;
    private String description;
    private String status;
    private int changeCount;
    private UserDoc embeddedUser;
    private String userId;

    @Builder.Default
    private List<String> tags = new ArrayList<>();
    private Instant createdAt;

    @Builder.Default
    private List<AttachmentMeta> attachments = new ArrayList<>();
}