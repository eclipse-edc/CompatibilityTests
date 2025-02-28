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

package org.eclipse.edc.compatibility.tests.transfer;

import jakarta.json.JsonObject;
import org.eclipse.edc.compatibility.tests.fixtures.BaseParticipant;
import org.eclipse.edc.compatibility.tests.fixtures.DockerRuntimeExtension;
import org.eclipse.edc.compatibility.tests.fixtures.EdcDockerRuntimes;
import org.eclipse.edc.compatibility.tests.fixtures.LocalParticipant;
import org.eclipse.edc.compatibility.tests.fixtures.RemoteParticipant;
import org.eclipse.edc.compatibility.tests.fixtures.Runtimes;
import org.eclipse.edc.connector.controlplane.test.system.utils.PolicyFixtures;
import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimePerClassExtension;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.sql.testfixtures.PostgresqlEndToEndExtension;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.connector.controlplane.test.system.utils.PolicyFixtures.noConstraintPolicy;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.STARTED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.SUSPENDED;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.util.io.Ports.getFreePort;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;


@EndToEndTest
public class TransferEndToEndTest {

    protected static final LocalParticipant LOCAL_PARTICIPANT = LocalParticipant.Builder.newInstance()
            .name("local")
            .id("local")
            .build();

    protected static final RemoteParticipant REMOTE_PARTICIPANT = RemoteParticipant.Builder.newInstance()
            .name("remote")
            .id("remote")
            .build();

    @Order(0)
    @RegisterExtension
    static final PostgresqlEndToEndExtension POSTGRESQL_EXTENSION = new PostgresqlEndToEndExtension();

    @Order(5)
    @RegisterExtension
    static final DockerRuntimeExtension DATA_PLANE_T = EdcDockerRuntimes.DATA_PLANE.create("dataplane")
            .envProvider(REMOTE_PARTICIPANT::dataPlaneEnv)
            .envProvider(pgEnv(REMOTE_PARTICIPANT.getName()));

    @Order(4)
    @RegisterExtension
    static final DockerRuntimeExtension CONTROL_PLANE_T = EdcDockerRuntimes.CONTROL_PLANE.create("controlplane")
            .envProvider(REMOTE_PARTICIPANT::controlPlaneEnv)
            .envProvider(pgEnv(REMOTE_PARTICIPANT.getName()));
    @Order(1)
    @RegisterExtension
    static final BeforeAllCallback CREATE_DATABASES = context -> {
        POSTGRESQL_EXTENSION.createDatabase(LOCAL_PARTICIPANT.getName());
        POSTGRESQL_EXTENSION.createDatabase(REMOTE_PARTICIPANT.getName());
    };

    @Order(2)
    @RegisterExtension
    static final RuntimeExtension LOCAL_CONTROL_PLANE = new RuntimePerClassExtension(
            Runtimes.CONTROL_PLANE.create("local-control-plane")
                    .configurationProvider(LOCAL_PARTICIPANT::controlPlaneConfiguration)
                    .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(LOCAL_PARTICIPANT.getName())));
    @Order(3)
    @RegisterExtension
    static final RuntimeExtension LOCAL_DATA_PLANE = new RuntimePerClassExtension(
            Runtimes.DATA_PLANE.create("local-data-plane")
                    .configurationProvider(LOCAL_PARTICIPANT::dataPlaneConfiguration)
                    .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(LOCAL_PARTICIPANT.getName()))
                    .registerSystemExtension(ServiceExtension.class, new HttpProxyDataPlaneExtension()));

    private static ClientAndServer providerDataSource;

    static Supplier<Map<String, String>> pgEnv(String databaseName) {
        return () -> {
            var cfg = POSTGRESQL_EXTENSION.configFor(databaseName);
            return cfg.getEntries().entrySet().stream()
                    .collect(Collectors.toMap(e -> toEnv(e.getKey()), Map.Entry::getValue));
        };
    }

    static String toEnv(String cfg) {
        return cfg.toUpperCase().replace('.', '_');
    }

    @BeforeAll
    static void beforeAll() {
        providerDataSource = startClientAndServer(getFreePort());
    }

    private static @NotNull Map<String, Object> httpSourceDataAddress() {
        return Map.of(
                EDC_NAMESPACE + "name", "transfer-test",
                EDC_NAMESPACE + "baseUrl", "http://localhost:" + providerDataSource.getPort() + "/source",
                EDC_NAMESPACE + "type", "HttpData",
                EDC_NAMESPACE + "proxyQueryParams", "true"
        );
    }

    @AfterEach
    void afterEach() {
        providerDataSource.reset();
    }

    @BeforeEach
    void storeKeys() {
        var vault = LOCAL_DATA_PLANE.getService(Vault.class);
        vault.storeSecret("private-key", LOCAL_PARTICIPANT.getPrivateKey());
        vault.storeSecret("public-key", LOCAL_PARTICIPANT.getPublicKey());
    }

    @ParameterizedTest
    @ArgumentsSource(ParticipantsArgProvider.class)
    void httpPullTransfer(BaseParticipant consumer, BaseParticipant provider, String protocol) {
        consumer.setProtocol(protocol);
        provider.setProtocol(protocol);
        provider.waitForDataPlane();
        providerDataSource.when(HttpRequest.request()).respond(HttpResponse.response().withBody("data"));
        var assetId = UUID.randomUUID().toString();
        var sourceDataAddress = httpSourceDataAddress();
        createResourcesOnProvider(provider, assetId, PolicyFixtures.contractExpiresIn("5s"), sourceDataAddress);

        var transferProcessId = consumer.requestAssetFrom(assetId, provider)
                .withTransferType("HttpData-PULL")
                .execute();

        consumer.awaitTransferToBeInState(transferProcessId, STARTED);

        var edr = await().atMost(consumer.getTimeout())
                .until(() -> consumer.getEdr(transferProcessId), Objects::nonNull);

        // Do the transfer
        var msg = UUID.randomUUID().toString();
        await().atMost(consumer.getTimeout())
                .untilAsserted(() -> consumer.pullData(edr, Map.of("message", msg), body -> assertThat(body).isEqualTo("data")));

        // checks that the EDR is gone once the contract expires
        await().atMost(consumer.getTimeout())
                .untilAsserted(() -> assertThatThrownBy(() -> consumer.getEdr(transferProcessId)));

        // checks that transfer fails
        await().atMost(consumer.getTimeout())
                .untilAsserted(() -> assertThatThrownBy(() -> consumer.pullData(edr, Map.of("message", msg), body -> assertThat(body).isEqualTo("data"))));


        if (provider.hasProxySupport()) {
            providerDataSource.verify(HttpRequest.request("/source").withMethod("GET"));
        }

    }

    @ParameterizedTest
    @ArgumentsSource(ParticipantsArgProvider.class)
    void suspendAndResume_httpPull_dataTransfer(BaseParticipant consumer, BaseParticipant provider, String protocol) {
        consumer.setProtocol(protocol);
        provider.setProtocol(protocol);
        provider.waitForDataPlane();
        providerDataSource.when(HttpRequest.request()).respond(HttpResponse.response().withBody("data"));
        var assetId = UUID.randomUUID().toString();
        createResourcesOnProvider(provider, assetId, PolicyFixtures.noConstraintPolicy(), httpSourceDataAddress());

        var transferProcessId = consumer.requestAssetFrom(assetId, provider)
                .withTransferType("HttpData-PULL")
                .execute();

        consumer.awaitTransferToBeInState(transferProcessId, STARTED);

        var edr = await().atMost(consumer.getTimeout()).until(() -> consumer.getEdr(transferProcessId), Objects::nonNull);

        var msg = UUID.randomUUID().toString();
        await().atMost(consumer.getTimeout()).untilAsserted(() -> consumer.pullData(edr, Map.of("message", msg), body -> assertThat(body).isEqualTo("data")));

        consumer.suspendTransfer(transferProcessId, "supension");

        consumer.awaitTransferToBeInState(transferProcessId, SUSPENDED);

        // checks that the EDR is gone once the transfer has been suspended
        await().atMost(consumer.getTimeout()).untilAsserted(() -> assertThatThrownBy(() -> consumer.getEdr(transferProcessId)));
        // checks that transfer fails
        await().atMost(consumer.getTimeout()).untilAsserted(() -> assertThatThrownBy(() -> consumer.pullData(edr, Map.of("message", msg), body -> assertThat(body).isEqualTo("data"))));

        consumer.resumeTransfer(transferProcessId);

        // check that transfer is available again
        consumer.awaitTransferToBeInState(transferProcessId, STARTED);
        var secondEdr = await().atMost(consumer.getTimeout()).until(() -> consumer.getEdr(transferProcessId), Objects::nonNull);
        var secondMessage = UUID.randomUUID().toString();
        await().atMost(consumer.getTimeout()).untilAsserted(() -> consumer.pullData(secondEdr, Map.of("message", secondMessage), body -> assertThat(body).isEqualTo("data")));

        if (provider.hasProxySupport()) {
            providerDataSource.verify(HttpRequest.request("/source").withMethod("GET"));
        }
    }

    protected void createResourcesOnProvider(BaseParticipant provider, String assetId, JsonObject contractPolicy, Map<String, Object> dataAddressProperties) {
        provider.createAsset(assetId, Map.of("description", "description"), dataAddressProperties);
        var contractPolicyId = provider.createPolicyDefinition(contractPolicy);
        var noConstraintPolicyId = provider.createPolicyDefinition(noConstraintPolicy());

        provider.createContractDefinition(assetId, UUID.randomUUID().toString(), noConstraintPolicyId, contractPolicyId);
    }

    private static class ParticipantsArgProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
            return Stream.of(
                    Arguments.of(REMOTE_PARTICIPANT, LOCAL_PARTICIPANT, "dataspace-protocol-http"),
                    Arguments.of(LOCAL_PARTICIPANT, REMOTE_PARTICIPANT, "dataspace-protocol-http"),
                    Arguments.of(REMOTE_PARTICIPANT, LOCAL_PARTICIPANT, "dataspace-protocol-http:2024/1"),
                    Arguments.of(LOCAL_PARTICIPANT, REMOTE_PARTICIPANT, "dataspace-protocol-http:2024/1")
            );
        }
    }

}
