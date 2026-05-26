package com.smarthome.controller;

import com.smarthome.dto.Dto;
import com.smarthome.service.ExecutiveDashboardService;
import com.smarthome.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ProductService productService;
    private final ExecutiveDashboardService executiveDashboardService;

    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_READ') or hasAuthority('REPORTS_READ')")
    public Dto.DashboardResponse dashboard(@AuthenticationPrincipal String userId) {
        return productService.getDashboard(userId);
    }

    @GetMapping("/executive")
    @PreAuthorize("hasAuthority('REPORTS_READ')")
    public Dto.ExecutiveDashboardDto executive() {
        return executiveDashboardService.executive();
    }
}
