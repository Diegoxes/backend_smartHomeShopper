package com.smarthome.controller;

import com.smarthome.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class WhatsAppController {

    private final WhatsAppService whatsAppService;

    @PostMapping(value = "/whatsapp",
            consumes = "application/x-www-form-urlencoded",
            produces = "application/xml")
    public String whatsapp(
            @RequestParam("From") String from,
            @RequestParam("Body") String body) {

        String reply = whatsAppService.handleIncoming(from, body);
        return twiml(reply);
    }

    private String twiml(String message) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
               "<Response><Message>" + message + "</Message></Response>";
    }
}
