/*
 *  Copyright (c) 2025 Cofinity-X
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Cofinity-X - initial API and implementation
 *
 */

package org.eclipse.edc.compatibility.tests.fixtures;

import org.eclipse.edc.junit.annotations.Runtime;
import org.eclipse.edc.junit.utils.Endpoints;
import org.eclipse.edc.junit.utils.LazySupplier;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.eclipse.edc.util.io.Ports.getFreePort;

public class DockerRuntimeExtension implements BeforeAllCallback, AfterAllCallback, ParameterResolver {

    protected final Map<Class<?>, LazySupplier<?>> paramProviders = new HashMap<>();
    private final List<Supplier<Map<String, String>>> envProviders = new ArrayList<>();
    private final GenericContainer<?> container;
    private final String name;
    private final Map<String, String> configuration = new HashMap<>();
    private Endpoints endpoints = Endpoints.Builder.newInstance().build();


    @SuppressWarnings("resource")
    public DockerRuntimeExtension(String image, String name) {
        this.name = name;
        container = new GenericContainer<>(image)
                .withCreateContainerCmdModifier(cmd -> cmd.withName(name))
                .withNetworkMode("host")
                .waitingFor(Wait.forLogMessage(".*Runtime .* ready.*", 1));
    }

    @Override
    public void afterAll(ExtensionContext context) {
        container.stop();
    }

    public DockerRuntimeExtension envProvider(Supplier<Map<String, String>> envProvider) {
        this.envProviders.add(envProvider);
        return this;
    }

    public DockerRuntimeExtension endpoints(Endpoints endpoints) {
        this.endpoints = endpoints;
        return this;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        var variables = envProviders.stream().map(Supplier::get)
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        var endpointsEnv = new HashMap<String, String>();
        this.endpoints.getEndpoints().forEach((key, endpoint) -> {
            if (key.equals("default")) {
                endpointsEnv.put("WEB_HTTP_PORT", String.valueOf(endpoint.get().getPort()));
                endpointsEnv.put("WEB_HTTP_PATH", endpoint.get().getPath());
            } else {
                endpointsEnv.put("WEB_HTTP_" + key.toUpperCase() + "_PORT", String.valueOf(endpoint.get().getPort()));
                endpointsEnv.put("WEB_HTTP_" + key.toUpperCase() + "_PATH", endpoint.get().getPath());
            }
        });
        // if the default endpoint is not set, set a random port and /api path
        if (!endpointsEnv.containsKey("web.http.port")) {
            endpointsEnv.put("WEB_HTTP_PORT", String.valueOf(getFreePort()));
            endpointsEnv.put("WEB_HTTP_PATH", "/api");
        }
        variables.putAll(endpointsEnv);
        configuration.putAll(variables);
        container.withEnv(configuration);
        container.start();
    }

    public <T> DockerRuntimeExtension paramProvider(Class<T> klass, Function<DockerRuntimeContext, T> paramProvider) {
        this.paramProviders.put(klass, new LazySupplier<>(() -> paramProvider.apply(new DockerRuntimeContext(endpoints, configuration))));
        return this;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        if (paramProviders.containsKey(parameterContext.getParameter().getType())) {
            return matchName(parameterContext);
        }
        return false;
    }

    protected boolean matchName(ParameterContext parameterContext) {
        return parameterContext.findAnnotation(Runtime.class)
                .map(Runtime::value)
                .map(name -> name.equals(this.name))
                .orElse(true);
    }


    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        if (paramProviders.containsKey(parameterContext.getParameter().getType())) {
            return paramProviders.get(parameterContext.getParameter().getType()).get();
        }
        return null;
    }
}
