package com.jdsu.quiz.platform;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/gateway")
public class GatewayController {
    private final ServiceRegistry serviceRegistry;
    private final WebClient webClient;

    public GatewayController(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
        this.webClient = WebClient.builder().build();
    }

    @GetMapping("/services")
    public List<ServiceRegistry.ServiceDescriptor> services() {
        return serviceRegistry.services();
    }

    @GetMapping("/services/{serviceId}/health")
    public ServiceHealth health(@PathVariable String serviceId) {
        ServiceRegistry.ServiceDescriptor service = serviceRegistry.resolve(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service is not registered."));
        try {
            String body = webClient.get()
                    .uri(service.baseUri().resolve(service.statusPath()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return new ServiceHealth(service.id(), "UP", body == null ? "{}" : body);
        } catch (Exception exception) {
            return new ServiceHealth(service.id(), "DOWN", exception.getMessage());
        }
    }

    public record ServiceHealth(String serviceId, String status, String details) {
    }
}
