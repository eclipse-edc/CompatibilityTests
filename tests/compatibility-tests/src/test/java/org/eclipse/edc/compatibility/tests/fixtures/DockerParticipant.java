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

import org.eclipse.edc.tests.fixtures.extension.cp.ControlPlaneApi;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.eclipse.edc.junit.testfixtures.TestUtils.getResourceFileContentAsString;
import static org.eclipse.edc.util.io.Ports.getFreePort;

public class DockerParticipant {

    private static final String API_KEY = "password";


    protected static String privateKey = getResourceFileContentAsString("certs/key.pem");
    protected static String publicKey = getResourceFileContentAsString("certs/cert.pem");

    protected final URI controlPlaneDefault = URI.create("http://localhost:" + getFreePort());
    protected final URI controlPlaneControl = URI.create("http://localhost:" + getFreePort() + "/control");
    protected final URI dataPlaneDefault = URI.create("http://localhost:" + getFreePort());
    protected final URI dataPlaneControl = URI.create("http://localhost:" + getFreePort() + "/control");
    protected final URI controlPlaneVersion = URI.create("http://localhost:" + getFreePort() + "/version");
    protected final URI dataPlaneVersion = URI.create("http://localhost:" + getFreePort() + "/version");
    protected ControlPlaneApi controlPlaneApi;

    public Map<String, String> controlPlaneEnv() {
        return new HashMap<>() {
            {
                put("EDC_PARTICIPANT_ID", controlPlaneApi.getId());
                put("WEB_HTTP_PORT", String.valueOf(controlPlaneDefault.getPort()));
                put("WEB_HTTP_PATH", "/api");
                put("WEB_HTTP_PROTOCOL_PORT", String.valueOf(controlPlaneApi.getControlPlaneProtocol().getPort()));
                put("WEB_HTTP_PROTOCOL_PATH", controlPlaneApi.getControlPlaneProtocol().getPath());
                put("WEB_HTTP_MANAGEMENT_PORT", String.valueOf(controlPlaneApi.getControlPlaneManagement().getPort()));
                put("WEB_HTTP_MANAGEMENT_PATH", controlPlaneApi.getControlPlaneManagement().getPath());
                put("WEB_HTTP_MANAGEMENT_AUTH_TYPE", "tokenbased");
                put("WEB_HTTP_MANAGEMENT_AUTH_KEY", API_KEY);
                put("WEB_HTTP_VERSION_PORT", String.valueOf(controlPlaneVersion.getPort()));
                put("WEB_HTTP_VERSION_PATH", controlPlaneVersion.getPath());
                put("WEB_HTTP_CONTROL_PORT", String.valueOf(controlPlaneControl.getPort()));
                put("WEB_HTTP_CONTROL_PATH", controlPlaneControl.getPath());
                put("EDC_DSP_CALLBACK_ADDRESS", controlPlaneApi.getControlPlaneProtocol().toString());
            }
        };
    }

    public ControlPlaneApi getControlPlaneApi() {
        return controlPlaneApi;
    }

    public Map<String, String> dataPlaneEnv() {
        return new HashMap<>() {
            {
                put("EDC_PARTICIPANT_ID", controlPlaneApi.getId());
                put("EDC_COMPONENT_ID", controlPlaneApi.getId());
                put("EDC_API_AUTH_KEY", API_KEY);
                put("WEB_HTTP_PORT", String.valueOf(dataPlaneDefault.getPort()));
                put("WEB_HTTP_PATH", "/api");
                put("WEB_HTTP_VERSION_PORT", String.valueOf(dataPlaneVersion.getPort()));
                put("WEB_HTTP_VERSION_PATH", dataPlaneVersion.getPath());
                put("WEB_HTTP_CONTROL_PORT", String.valueOf(dataPlaneControl.getPort()));
                put("WEB_HTTP_CONTROL_PATH", dataPlaneControl.getPath());
                put("EDC_TRANSFER_PROXY_TOKEN_SIGNER_PRIVATEKEY_ALIAS", "private-key");
                put("EDC_TRANSFER_PROXY_TOKEN_VERIFIER_PUBLICKEY_ALIAS", "public-key");
                put("EDC_DPF_SELECTOR_URL", controlPlaneControl + "/v1/dataplanes");
                put("TESTING_EDC_VAULTS_1_KEY", "private-key");
                put("TESTING_EDC_VAULTS_1_VALUE", privateKey);
                put("TESTING_EDC_VAULTS_2_KEY", "public-key");
                put("TESTING_EDC_VAULTS_2_VALUE", publicKey);

            }
        };
    }

    public static class Builder {

        private final DockerParticipant participant;

        private final ControlPlaneApi.Builder controlPlaneApiBuilder = ControlPlaneApi.Builder.newInstance();

        protected Builder() {
            participant = new DockerParticipant();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder id(String id) {
            this.controlPlaneApiBuilder.id(id);
            return this;
        }

        public Builder name(String name) {
            this.controlPlaneApiBuilder.name(name);
            return this;
        }

        public DockerParticipant build() {
            participant.controlPlaneApi = controlPlaneApiBuilder.build();
            return participant;
        }
    }
}
