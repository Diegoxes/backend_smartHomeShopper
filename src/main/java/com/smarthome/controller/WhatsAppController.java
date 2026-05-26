package com.smarthome.controller;

import com.smarthome.dto.WhatsAppReply;
import com.smarthome.entity.WhatsAppReportDownload;
import com.smarthome.service.WhatsAppReportTokenService;
import com.smarthome.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class WhatsAppController {

    private final WhatsAppService whatsAppService;
    private final WhatsAppReportTokenService reportTokenService;

    @PostMapping(value = "/whatsapp",
            consumes = "application/x-www-form-urlencoded",
            produces = "application/xml")
    public String whatsapp(
            @RequestParam("From") String from,
            @RequestParam("Body") String body) {

        WhatsAppReply reply = whatsAppService.handleIncoming(from, body);
        return twiml(reply);
    }

    @GetMapping("/reports/{token}")
    public ResponseEntity<byte[]> downloadReport(@PathVariable String token) {
        return reportTokenService.load(token)
                .map(this::toDownloadResponse)
                .orElse(ResponseEntity.notFound().build());
    }

    private ResponseEntity<byte[]> toDownloadResponse(WhatsAppReportDownload row) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + row.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(row.getContentType()))
                .body(row.getData());
    }

    private String twiml(WhatsAppReply reply) {
        String escapedBody = escapeXml(reply.body());
        if (reply.mediaUrl() != null && !reply.mediaUrl().isBlank()) {
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<Response><Message><Body>" + escapedBody + "</Body>" +
                    "<Media>" + escapeXml(reply.mediaUrl()) + "</Media></Message></Response>";
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<Response><Message>" + escapedBody + "</Message></Response>";
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
