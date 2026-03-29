package com.instituteops.web;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class HomeController {

    @GetMapping("/")
    public String index() {
        return "redirect:/dashboard";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        model.addAttribute("username", authentication.getName());
        return "dashboard";
    }

    @GetMapping("/admin")
    public String admin() {
        return "role-page";
    }

    @GetMapping("/registrar")
    public String registrar() {
        return "role-page";
    }

    @GetMapping("/instructor")
    public String instructor() {
        return "role-page";
    }

    @GetMapping("/procurement")
    public String procurement() {
        return "role-page";
    }

    @ResponseBody
    @RequestMapping("/api/internal/ping")
    public String internalPing() {
        return "pong";
    }
}
