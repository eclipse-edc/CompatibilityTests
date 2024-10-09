/*
 *  Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */

package org.eclipse.edc.compatibility.tests.fixtures;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.Map;

public enum EdcDockerRuntimes {

    CONTROL_PLANE(
            "controlplane-stable:latest"
    ),

    DATA_PLANE(
            "dataplane-stable:latest"
    );

    private final String image;

    EdcDockerRuntimes(String image) {
        this.image = image;
    }

    public GenericContainer<?> create(String name, Map<String, String> env) {
        return new GenericContainer<>(image)
                .withCreateContainerCmdModifier(cmd -> cmd.withName(name))
                .withNetworkMode("host")
                .waitingFor(Wait.forLogMessage(".*Runtime .* ready.*", 1))
                .withEnv(env);
    }
}
