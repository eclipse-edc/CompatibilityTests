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

import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;

import java.util.HashMap;
import java.util.Map;

import static org.eclipse.edc.boot.BootServicesExtension.PARTICIPANT_ID;
import static org.eclipse.edc.util.io.Ports.getFreePort;

public class LocalParticipant extends BaseParticipant {

    private static final String API_KEY = "password";

    private final int httpProvisionerPort = getFreePort();

    public Config controlPlaneConfiguration() {
        return ConfigFactory.fromMap(new HashMap<>() {
            {
                put(PARTICIPANT_ID, id);
                put("web.http.port", String.valueOf(controlPlaneDefault.getPort()));
                put("web.http.path", "/api");
                put("web.http.protocol.port", String.valueOf(controlPlaneProtocol.get().getPort()));
                put("web.http.protocol.path", controlPlaneProtocol.get().getPath());
                put("web.http.management.port", String.valueOf(controlPlaneManagement.get().getPort()));
                put("web.http.management.path", controlPlaneManagement.get().getPath());
                put("web.http.management.auth.type", "tokenbased");
                put("web.http.management.auth.key", API_KEY);
                put("web.http.version.port", String.valueOf(controlPlaneVersion.getPort()));
                put("web.http.version.path", controlPlaneVersion.getPath());
                put("web.http.control.port", String.valueOf(controlPlaneControl.getPort()));
                put("web.http.control.path", controlPlaneControl.getPath());
                put("edc.dsp.callback.address", controlPlaneProtocol.get().toString());
                put("edc.transfer.proxy.endpoint", dataPlanePublic.toString());
                put("edc.transfer.send.retry.limit", "1");
                put("edc.transfer.send.retry.base-delay.ms", "100");
                put("edc.negotiation.consumer.send.retry.limit", "1");
                put("edc.negotiation.provider.send.retry.limit", "1");
                put("edc.negotiation.consumer.send.retry.base-delay.ms", "100");
                put("edc.negotiation.provider.send.retry.base-delay.ms", "100");

                put("edc.negotiation.consumer.state-machine.iteration-wait-millis", "50");
                put("edc.negotiation.provider.state-machine.iteration-wait-millis", "50");
                put("edc.transfer.state-machine.iteration-wait-millis", "50");

                put("provisioner.http.entries.default.provisioner.type", "provider");
                put("provisioner.http.entries.default.endpoint", "http://localhost:%d/provision".formatted(httpProvisionerPort));
                put("provisioner.http.entries.default.data.address.type", "HttpProvision");
            }
        });
    }

    public Config dataPlaneConfiguration() {
        return ConfigFactory.fromMap(new HashMap<>() {
            {
                put("web.http.port", String.valueOf(dataPlaneDefault.getPort()));
                put("web.http.path", "/api");
                put("web.http.public.port", String.valueOf(dataPlanePublic.getPort()));
                put("web.http.public.path", "/public");
                put("web.http.control.port", String.valueOf(dataPlaneControl.getPort()));
                put("web.http.control.path", dataPlaneControl.getPath());
                put("edc.dataplane.api.public.baseurl", dataPlanePublic + "/v2/");
                put("edc.dataplane.token.validation.endpoint", controlPlaneControl + "/token");
                put("edc.transfer.proxy.token.signer.privatekey.alias", "private-key");
                put("edc.transfer.proxy.token.verifier.publickey.alias", "public-key");
                put("edc.dataplane.http.sink.partition.size", "1");
                put("edc.dataplane.state-machine.iteration-wait-millis", "50");
                put("edc.dpf.selector.url", controlPlaneControl + "/v1/dataplanes");
                put("edc.component.id", "dataplane");
            }
        });
    }

    @Override
    public boolean hasProxySupport() {
        return false;
    }

    public static class Builder extends BaseParticipant.Builder<LocalParticipant, Builder> {

        protected Builder() {
            super(new LocalParticipant());
        }

        public static Builder newInstance() {
            return new Builder();
        }

        @Override
        public LocalParticipant build() {
            super.build();
            var headers = Map.of("x-api-key", API_KEY);
            participant.enrichManagementRequest = req -> req.headers(headers);
            return participant;
        }
    }
}
