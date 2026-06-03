package com.jdsu.quiz.platform;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public interface ServiceRegistry {
    List<ServiceDescriptor> services();

    Optional<ServiceDescriptor> resolve(String serviceId);

    record ServiceDescriptor(String id, String name, URI baseUri, String statusPath, boolean localModule) {
    }
}
