package com.fintrack.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * When the built React app is served by Spring Boot, sends browser routes such as
 * /app/bills to index.html so a page refresh doesn't 404.
 */
@Controller
public class SpaController {

    @GetMapping({"/app", "/app/**"})
    public String forward() {
        return "forward:/index.html";
    }
}
