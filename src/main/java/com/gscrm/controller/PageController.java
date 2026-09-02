package com.gscrm.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String calendar() {
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/change-password")
    public String changePassword() {
        return "change-password";
    }

    @GetMapping("/privacy")
    public String privacy() {
        return "privacy";
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard";
    }

    @GetMapping("/services")
    public String services() {
        return "services";
    }

    @GetMapping("/staff")
    public String staff() {
        return "staff";
    }

    @GetMapping("/resources")
    public String resources() {
        return "resources";
    }

    @GetMapping("/audit")
    public String audit() {
        return "audit";
    }

    @GetMapping("/customers")
    public String customers() {
        return "customers";
    }

    @GetMapping("/expenses")
    public String expenses() {
        return "expenses";
    }

    @GetMapping("/products")
    public String products() {
        return "products";
    }

    @GetMapping("/booking")
    public String booking() {
        return "booking";
    }

    @GetMapping("/customer/login")
    public String customerLogin() {
        return "customer-login";
    }

    @GetMapping("/customer/register")
    public String customerRegister() {
        return "customer-register";
    }

    @GetMapping("/customer/portal")
    public String customerPortal() {
        return "customer-portal";
    }

    @GetMapping("/campaigns")
    public String campaigns() {
        return "campaigns";
    }

    @GetMapping("/users")
    public String users() {
        return "users";
    }

    @GetMapping("/settings")
    public String settings() {
        return "settings";
    }

    @GetMapping("/settings/billing")
    public String billing() {
        return "billing";
    }

    @GetMapping("/platform/tenants")
    public String platformTenants() {
        return "platform/tenants";
    }

    @GetMapping("/org/dashboard")
    public String orgDashboard() {
        return "org/dashboard";
    }

    @GetMapping("/onboarding/wizard")
    public String onboardingWizard() {
        return "onboarding/wizard";
    }

    @GetMapping("/onboarding/setup")
    public String onboardingSetup() {
        return "onboarding/setup";
    }
}
