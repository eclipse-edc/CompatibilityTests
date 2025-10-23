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

package org.eclipse.edc.tests.fixtures;

import org.eclipse.edc.junit.utils.Endpoints;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

import static org.eclipse.edc.util.io.Ports.getFreePort;
import static org.eclipse.edc.web.spi.configuration.ApiContext.CONTROL;
import static org.eclipse.edc.web.spi.configuration.ApiContext.MANAGEMENT;
import static org.eclipse.edc.web.spi.configuration.ApiContext.PROTOCOL;

public class Runtimes {


    public interface Issuer {
        String ID = "issuer";
        String[] MODULES = new String[]{
                ":runtimes:snapshot:issuer-snapshot"
        };

    }

    public interface IdentityHub {
        String[] MODULES = new String[]{
                ":runtimes:snapshot:identity-hub-snapshot"
        };

        static String didFor(Endpoints endpoints, String participantContextId) {
            var didEndpoint = Objects.requireNonNull(endpoints.getEndpoint("did"));
            String didLocation = String.format("%s%%3A%s", didEndpoint.get().getHost(), didEndpoint.get().getPort());
            return String.format("did:web:%s:%s", didLocation, participantContextId);
        }

        static Config dcpConfig(Endpoints endpoints, String participantContextId) {
            var did = didFor(endpoints, participantContextId);
            var stsEndpoint = Objects.requireNonNull(endpoints.getEndpoint("sts"));
            return ConfigFactory.fromMap(Map.of(
                    "edc.participant.id", did,
                    "edc.iam.issuer.id", did,
                    "edc.iam.sts.oauth.client.id", did,
                    "edc.iam.sts.oauth.client.secret.alias", did + "-alias",
                    "edc.iam.sts.oauth.token.url", stsEndpoint.get().toString() + "/token",
                    "edc.iam.did.web.use.https", "false"
            ));
        }
    }

    public interface DataPlane {
        String[] MODULES = new String[]{
                ":runtimes:snapshot:dataplane-snapshot"
        };

        Endpoints.Builder ENDPOINTS = Endpoints.Builder.newInstance()
                .endpoint(CONTROL, () -> URI.create("http://localhost:" + getFreePort() + "/control"));

        static Config config() {
            return ConfigFactory.fromMap(Map.of(
                    "edc.transfer.proxy.token.signer.privatekey.alias", "private-key",
                    "edc.transfer.proxy.token.verifier.publickey.alias", "public-key"
            ));
        }
    }

    public interface ControlPlane {
        String API_KEY = "password";

        String[] MODULES = new String[]{
                ":runtimes:snapshot:controlplane-snapshot"
        };

        String[] DCP_MODULES = new String[]{
                ":runtimes:snapshot:controlplane-snapshot-dcp"
        };

        Endpoints.Builder ENDPOINTS = Endpoints.Builder.newInstance()
                .endpoint(MANAGEMENT, () -> URI.create("http://localhost:" + getFreePort() + "/management"))
                .endpoint(CONTROL, () -> URI.create("http://localhost:" + getFreePort() + "/control"))
                .endpoint(PROTOCOL, () -> URI.create("http://localhost:" + getFreePort() + "/protocol"));


        static Config config(String participantId) {
            return ConfigFactory.fromMap(Map.of(
                    "edc.participant.id", participantId,
                    "web.http.management.auth.type", "tokenbased",
                    "web.http.management.auth.key", API_KEY));
        }

        static Config dataPlaneSelectorFor(Endpoints endpoints) {
            var controlEndpoint = Objects.requireNonNull(endpoints.getEndpoint("control"));
            return ConfigFactory.fromMap(Map.of(
                    "edc.dpf.selector.url", controlEndpoint.get() + "/v1/dataplanes"
            ));
        }

    }

}
