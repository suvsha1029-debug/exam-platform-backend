package com.shashwat.examplatformbackend.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TemplateVaultController {
    @GetMapping({"/template-vault", "/template-vault/"})
    public String templateVault() {
        return "redirect:/template-vault/index.html";
    }
}
