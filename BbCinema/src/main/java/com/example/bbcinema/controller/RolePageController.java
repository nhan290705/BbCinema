package com.example.bbcinema.controller;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class RolePageController {
    @GetMapping("/api/admin/dashboard")
    public Map<String, String> admin() { return Map.of("message", "Trang quản trị", "role", "ADMIN"); }
    @GetMapping("/api/manager/dashboard")
    public Map<String, String> manager() { return Map.of("message", "Trang quản lý", "role", "MANAGER"); }
    @GetMapping("/api/staff/dashboard")
    public Map<String, String> staff() { return Map.of("message", "Trang nhân viên", "role", "STAFF"); }
    @GetMapping("/api/customer/dashboard")
    public Map<String, String> customer() { return Map.of("message", "Trang khách hàng", "role", "CUSTOMER"); }
}
