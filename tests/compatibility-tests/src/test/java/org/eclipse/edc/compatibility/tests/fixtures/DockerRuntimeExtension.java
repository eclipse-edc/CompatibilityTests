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

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DockerRuntimeExtension implements BeforeAllCallback, AfterAllCallback {

    private final String image;
    private final String name;
    private final List<Supplier<Map<String, String>>> envProviders = new ArrayList<>();
    private GenericContainer<?> container;


    public DockerRuntimeExtension(String image, String name) {
        this.image = image;
        this.name = name;
        container = new GenericContainer<>(image)
                .withCreateContainerCmdModifier(cmd -> cmd.withName(name))
                .withNetworkMode("host")
                .waitingFor(Wait.forLogMessage(".*Runtime .* ready.*", 1));
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        container.stop();
    }

    public DockerRuntimeExtension envProvider(Supplier<Map<String, String>> envProvider) {
        this.envProviders.add(envProvider);
        return this;
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        var variables = envProviders.stream().map(Supplier::get)
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        container.withEnv(variables);
        container.start();
    }
}
