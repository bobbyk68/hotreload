package com.example.drools;

import com.example.drools.model.Customer;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DroolsService {

    private final KieContainer kieContainer;

    public DroolsService(KieContainer kieContainer) {
        this.kieContainer = kieContainer;
    }

    public List<String> fireAllRules() {
        // use the session name defined in your kmodule.xml, e.g., "ksession-rules"
        KieSession session = kieContainer.newKieSession("ksession-rules");
        List<String> firedRules = new ArrayList<>();

        // sample fact
        Customer customer = new Customer();
        customer.setAge(42);
        session.insert(customer);

        session.addEventListener(new DefaultAgendaEventListener() {
            @Override
            public void afterMatchFired(AfterMatchFiredEvent event) {
                firedRules.add(event.getMatch().getRule().getName());
            }
        });

        session.fireAllRules();
        session.dispose();

        return firedRules;
    }
}