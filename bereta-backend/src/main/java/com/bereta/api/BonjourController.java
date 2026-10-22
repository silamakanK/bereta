package com.bereta.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BonjourController {

    @GetMapping("/bonjour")
    public String bonjour() {
        return "Bonjour depuis BERETA !";
    }
}
