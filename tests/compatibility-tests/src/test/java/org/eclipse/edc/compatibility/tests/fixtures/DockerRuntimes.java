/*
 *  Copyright (c) 2025 Metaform Systems, Inc.
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Metaform Systems, Inc. - initial API and implementation
 *
 */

package org.eclipse.edc.compatibility.tests.fixtures;

import org.eclipse.edc.junit.utils.Endpoints;
import org.eclipse.edc.tests.fixtures.extension.cp.ControlPlaneApi;

import java.net.URI;
import java.util.Map;

import static org.eclipse.edc.junit.testfixtures.TestUtils.getResourceFileContentAsString;
import static org.eclipse.edc.util.io.Ports.getFreePort;
import static org.eclipse.edc.web.spi.configuration.ApiContext.CONTROL;
import static org.eclipse.edc.web.spi.configuration.ApiContext.MANAGEMENT;
import static org.eclipse.edc.web.spi.configuration.ApiContext.PROTOCOL;

public interface DockerRuntimes {

    interface ControlPlane {
        String IMAGE = "controlplane-stable:latest";

        Endpoints.Builder ENDPOINTS = Endpoints.Builder.newInstance()
                .endpoint(MANAGEMENT, () -> URI.create("http://localhost:" + getFreePort() + "/management"))
                .endpoint(CONTROL, () -> URI.create("http://localhost:" + getFreePort() + "/control"))
                .endpoint(PROTOCOL, () -> URI.create("http://localhost:" + getFreePort() + "/protocol"));

        static DockerRuntimeExtension create(String name) {
            return new DockerRuntimeExtension(IMAGE, name);
        }

        static Map<String, String> env(String participantId) {
            return Map.of("EDC_PARTICIPANT_ID", participantId);
        }

        static Map<String, String> dataPlaneSelectorFor(Endpoints endpoints) {
            return Map.of("EDC_DPF_SELECTOR_URL", endpoints.getEndpoint("control").get() + "/v1/dataplanes");
        }

        static ControlPlaneApi controlPlaneApi(DockerRuntimeContext ctx) {
            var id = ctx.getConfig().get("EDC_PARTICIPANT_ID");
            return ControlPlaneApi.Builder.newInstance()
                    .id(id)
                    .name("name")
                    .managementUrl(ctx.getEndpoint(MANAGEMENT))
                    .protocolUrl(ctx.getEndpoint(PROTOCOL))
                    .build();
        }
    }

    interface DataPlane {
        String IMAGE = "dataplane-stable:latest";

        Endpoints.Builder ENDPOINTS = Endpoints.Builder.newInstance()
                .endpoint(CONTROL, () -> URI.create("http://localhost:" + getFreePort() + "/control"));

        String PRIVATE_KEY = getResourceFileContentAsString("certs/key.pem");
        String PUBLIC_KEY = getResourceFileContentAsString("certs/cert.pem");

        static DockerRuntimeExtension create(String name) {
            return new DockerRuntimeExtension(IMAGE, name);
        }

        static Map<String, String> env() {
            return Map.of(
                    "EDC_TRANSFER_PROXY_TOKEN_SIGNER_PRIVATEKEY_ALIAS", "private-key",
                    "EDC_TRANSFER_PROXY_TOKEN_VERIFIER_PUBLICKEY_ALIAS", "public-key",
                    "TESTING_EDC_VAULTS_1_KEY", "private-key",
                    "TESTING_EDC_VAULTS_1_VALUE", PRIVATE_KEY,
                    "TESTING_EDC_VAULTS_2_KEY", "public-key",
                    "TESTING_EDC_VAULTS_2_VALUE", PUBLIC_KEY
            );
        }
    }
}
