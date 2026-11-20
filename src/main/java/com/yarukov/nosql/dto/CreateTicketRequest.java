package com.yarukov.nosql.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yarukov.nosql.model.couchdb.AttachmentMeta;
import com.yarukov.nosql.model.couchdb.TicketTag;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateTicketRequest(String userId, String title, String description,
                                  List<TicketTag> tags, List<AttachmentMeta> attachments) {}
