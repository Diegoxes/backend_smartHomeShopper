package com.smarthome.controller;

import com.smarthome.dto.Dto;
import com.smarthome.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ProductService productService;

    @GetMapping
    public Dto.DashboardResponse dashboard(@AuthenticationPrincipal String userId) {
        return productService.getDashboard(userId);
    }
}
