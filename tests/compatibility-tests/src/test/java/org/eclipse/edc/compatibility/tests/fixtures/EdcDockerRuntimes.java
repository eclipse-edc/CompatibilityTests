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

import java.util.Map;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.yaml.snakeyaml.util.Tuple;

public enum EdcDockerRuntimes {
    STABLE_CONNECTOR_0_10_0("controlplane-010:latest", "dataplane-010:latest"),

    STABLE_CONNECTOR("controlplane-stable:latest", "dataplane-stable:latest");

    private final String controlPlaneImage;
    private final String dataPlaneImage;

    EdcDockerRuntimes(String controlPlaneImage, String dataPlaneImage) {
        this.controlPlaneImage = controlPlaneImage;
        this.dataPlaneImage = dataPlaneImage;
    }

    public Tuple<GenericContainer<?>, GenericContainer<?>> start(Map<String, String> controlPlaneEnv, Map<String, String> dataPlaneEnv) {
        var controlPlane =
                new GenericContainer<>(controlPlaneImage).withCreateContainerCmdModifier(cmd -> cmd.withName(this.name() + "-controlplane")).withNetworkMode("host").waitingFor(Wait.forLogMessage(".*Runtime .* ready.*", 1)).withEnv(controlPlaneEnv);
        var dataPlane = new GenericContainer<>(dataPlaneImage).withCreateContainerCmdModifier(cmd -> cmd.withName(this.name() + "-dataplane")).withNetworkMode("host").waitingFor(Wait.forLogMessage(".*Runtime .* ready.*", 1)).withEnv(dataPlaneEnv);
        controlPlane.start();
        dataPlane.start();
        return new Tuple<>(controlPlane, dataPlane);
    }
}
