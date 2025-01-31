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

import java.util.HashMap;
import java.util.Map;

public class RemoteParticipant extends BaseParticipant {

    private static final String API_KEY = "password";

    public Map<String, String> controlPlaneEnv() {
        return new HashMap<>() {
            {
                put("EDC_PARTICIPANT_ID", id);
                put("EDC_API_AUTH_KEY", API_KEY);
                put("WEB_HTTP_PORT", String.valueOf(controlPlaneDefault.getPort()));
                put("WEB_HTTP_PATH", "/api");
                put("WEB_HTTP_PROTOCOL_PORT", String.valueOf(controlPlaneProtocol.get().getPort()));
                put("WEB_HTTP_PROTOCOL_PATH", controlPlaneProtocol.get().getPath());
                put("WEB_HTTP_MANAGEMENT_PORT", String.valueOf(controlPlaneManagement.get().getPort()));
                put("WEB_HTTP_MANAGEMENT_PATH", controlPlaneManagement.get().getPath());
                put("WEB_HTTP_VERSION_PORT", String.valueOf(controlPlaneVersion.getPort()));
                put("WEB_HTTP_VERSION_PATH", controlPlaneVersion.getPath());
                put("WEB_HTTP_CONTROL_PORT", String.valueOf(controlPlaneControl.getPort()));
                put("WEB_HTTP_CONTROL_PATH", controlPlaneControl.getPath());
                put("EDC_DSP_CALLBACK_ADDRESS", controlPlaneProtocol.get().toString());
                put("EDC_TRANSFER_PROXY_ENDPOINT", dataPlanePublic.toString());
                put("EDC_DATASOURCE_DEFAULT_URL", "jdbc:postgresql://localhost:5432/%s".formatted(getId()));
                put("EDC_DATASOURCE_DEFAULT_USER", "postgres");
                put("EDC_DATASOURCE_DEFAULT_PASSWORD", "password");
                put("EDC_SQL_SCHEMA_AUTOCREATE", "true");
            }
        };
    }

    public Map<String, String> dataPlaneEnv() {
        return new HashMap<>() {
            {
                put("WEB_HTTP_MANAGEMENT_AUTH_KEY", API_KEY);
                put("WEB_HTTP_MANAGEMENT_AUTH_TYPE", "tokenbased");
                put("WEB_HTTP_MANAGEMENT_AUTH_CONTEXT", "management-api");
                put("EDC_PARTICIPANT_ID", id);
                put("EDC_COMPONENT_ID", id);
                put("WEB_HTTP_PORT", String.valueOf(dataPlaneDefault.getPort()));
                put("WEB_HTTP_PATH", "/api");
                put("WEB_HTTP_VERSION_PORT", String.valueOf(dataPlaneVersion.getPort()));
                put("WEB_HTTP_VERSION_PATH", dataPlaneVersion.getPath());
                put("WEB_HTTP_PUBLIC_PORT", String.valueOf(dataPlanePublic.getPort()));
                put("WEB_HTTP_PUBLIC_PATH", "/public");
                put("EDC_DATAPLANE_API_PUBLIC_BASEURL", dataPlanePublic + "/v2/");
                put("WEB_HTTP_CONTROL_PORT", String.valueOf(dataPlaneControl.getPort()));
                put("WEB_HTTP_CONTROL_PATH", dataPlaneControl.getPath());
                put("EDC_DATASOURCE_DEFAULT_URL", "jdbc:postgresql://localhost:5432/%s".formatted(getId()));
                put("EDC_DATASOURCE_DEFAULT_USER", "postgres");
                put("EDC_DATASOURCE_DEFAULT_PASSWORD", "password");
                put("EDC_SQL_SCHEMA_AUTOCREATE", "true");
                put("EDC_TRANSFER_PROXY_TOKEN_SIGNER_PRIVATEKEY_ALIAS", "private-key");
                put("EDC_TRANSFER_PROXY_TOKEN_VERIFIER_PUBLICKEY_ALIAS", "public-key");
                put("EDC_DPF_SELECTOR_URL", controlPlaneControl + "/v1/dataplanes");
                put("TESTING_EDC_VAULTS_1_KEY", "private-key");
                put("TESTING_EDC_VAULTS_1_VALUE", getPrivateKey());
                put("TESTING_EDC_VAULTS_2_KEY", "public-key");
                put("TESTING_EDC_VAULTS_2_VALUE", getPublicKey());

            }
        };
    }

    public static class Builder extends BaseParticipant.Builder<RemoteParticipant, Builder> {

        protected Builder() {
            super(new RemoteParticipant());
        }

        public static Builder newInstance() {
            return new Builder();
        }

        @Override
        public RemoteParticipant build() {
            super.build();
            var headers = Map.of("x-api-key", API_KEY);
            participant.enrichManagementRequest = req -> req.headers(headers);
            return participant;
        }
    }
}
