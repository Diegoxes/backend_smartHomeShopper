package com.smarthome.dto;

public record WhatsAppReply(String body, String mediaUrl) {

    public static WhatsAppReply textOnly(String body) {
        return new WhatsAppReply(body, null);
    }

    public static WhatsAppReply withMedia(String body, String mediaUrl) {
        return new WhatsAppReply(body, mediaUrl);
    }
}
