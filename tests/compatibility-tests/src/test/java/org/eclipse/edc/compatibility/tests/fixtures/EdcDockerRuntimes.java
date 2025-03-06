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

    public DockerRuntimeExtension create(String name) {
        return new DockerRuntimeExtension(image, name);
    }
}
