package com.example.drools;

import java.util.List;
import com.example.drools.DroolsService;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/rules")
public class RuleController {

    private final DroolsService droolsService;

    public RuleController(DroolsService droolsService) {
        this.droolsService = droolsService;
    }

    @PostMapping("/fire")
    public String fire() {
        List<String> fired = droolsService.fireAllRules();
        return "Fired rules: " + fired;
    }
}