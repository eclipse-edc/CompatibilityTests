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
import org.eclipse.edc.compatibility.tests.fixtures.DockerRuntimeExtension;
import org.eclipse.edc.compatibility.tests.fixtures.DockerRuntimes;
import org.eclipse.edc.connector.controlplane.test.system.utils.PolicyFixtures;
import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.junit.annotations.Runtime;
import org.eclipse.edc.junit.extensions.ComponentRuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.junit.utils.Endpoints;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.sql.testfixtures.PostgresqlEndToEndExtension;
import org.eclipse.edc.tests.fixtures.Runtimes;
import org.eclipse.edc.tests.fixtures.extension.cp.ControlPlaneApi;
import org.eclipse.edc.tests.fixtures.transfer.HttpProxyDataPlaneExtension;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

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
import static org.eclipse.edc.junit.testfixtures.TestUtils.getResourceFileContentAsString;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.tests.fixtures.Runtimes.ControlPlane.dataPlaneSelectorFor;


@EndToEndTest
public class TransferEndToEndTest {

    public static final String REMOTE = "remote";
    public static final String LOCAL = "local";
    public static final String LOCAL_CP = "local-controlplane";
    public static final String LOCAL_DP = "local-dataplane";
    public static final String REMOTE_CP = "remote-controlplane";
    static final Endpoints LOCAL_CP_ENDPOINTS = Runtimes.ControlPlane.ENDPOINTS.build();
    static final Endpoints REMOTE_CP_ENDPOINTS = DockerRuntimes.ControlPlane.ENDPOINTS.build();

    @Order(0)
    @RegisterExtension
    static final PostgresqlEndToEndExtension POSTGRESQL_EXTENSION = new PostgresqlEndToEndExtension();

    @Order(2)
    @RegisterExtension
    static final DockerRuntimeExtension CONTROL_PLANE_T = DockerRuntimes.ControlPlane.create(REMOTE_CP)
            .endpoints(REMOTE_CP_ENDPOINTS)
            .envProvider(() -> DockerRuntimes.ControlPlane.env(REMOTE))
            .envProvider(pgEnv(REMOTE))
            .paramProvider(ControlPlaneApi.class, DockerRuntimes.ControlPlane::controlPlaneApi);

    @Order(3)
    @RegisterExtension
    static final DockerRuntimeExtension DATA_PLANE_T = DockerRuntimes.DataPlane.create("dataplane")
            .endpoints(DockerRuntimes.DataPlane.ENDPOINTS.build())
            .envProvider(() -> DockerRuntimes.ControlPlane.dataPlaneSelectorFor(REMOTE_CP_ENDPOINTS))
            .envProvider(DockerRuntimes.DataPlane::env)
            .envProvider(pgEnv(REMOTE));
    @Order(2)
    @RegisterExtension
    static final RuntimeExtension LOCAL_CONTROL_PLANE = ComponentRuntimeExtension.Builder.newInstance()
            .name(LOCAL_CP)
            .modules(Runtimes.ControlPlane.MODULES)
            .endpoints(LOCAL_CP_ENDPOINTS)
            .configurationProvider(() -> Runtimes.ControlPlane.config(LOCAL_CP))
            .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(LOCAL))
            .paramProvider(ControlPlaneApi.class, ControlPlaneApi::forContext)
            .build();
    @Order(3)
    @RegisterExtension
    static final RuntimeExtension LOCAL_DATA_PLANE = ComponentRuntimeExtension.Builder.newInstance()
            .name(LOCAL_DP)
            .modules(Runtimes.DataPlane.MODULES)
            .endpoints(Runtimes.DataPlane.ENDPOINTS.build())
            .configurationProvider(Runtimes.DataPlane::config)
            .configurationProvider(() -> dataPlaneSelectorFor(LOCAL_CP_ENDPOINTS))
            .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(LOCAL))
            .build()
            .registerSystemExtension(ServiceExtension.class, new HttpProxyDataPlaneExtension());

    @Order(1)
    @RegisterExtension
    static final BeforeAllCallback CREATE_DATABASES = context -> {
        POSTGRESQL_EXTENSION.createDatabase(LOCAL);
        POSTGRESQL_EXTENSION.createDatabase(REMOTE);
    };


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


    private static @NotNull Map<String, Object> httpSourceDataAddress() {
        return Map.of(
                EDC_NAMESPACE + "name", "transfer-test",
                EDC_NAMESPACE + "baseUrl", "http://localhost",
                EDC_NAMESPACE + "type", "HttpData",
                EDC_NAMESPACE + "proxyQueryParams", "true"
        );
    }

    @BeforeEach
    void storeKeys(@Runtime(LOCAL_DP) Vault vault) {
        var privateKey = getResourceFileContentAsString("certs/key.pem");
        var publicKey = getResourceFileContentAsString("certs/cert.pem");
        vault.storeSecret("private-key", privateKey);
        vault.storeSecret("public-key", publicKey);
    }

    @ParameterizedTest
    @ArgumentsSource(ParticipantsArgProvider.class)
    void httpPullTransfer_whenConsumerRemote(String protocol, String path, @Runtime(LOCAL_CP) ControlPlaneApi local, @Runtime(REMOTE_CP) ControlPlaneApi remote) {
        httpPullTransfer(protocol, path, remote, local);
    }

    @ParameterizedTest
    @ArgumentsSource(ParticipantsArgProvider.class)
    void httpPullTransfer_whenConsumerLocal(String protocol, String path, @Runtime(LOCAL_CP) ControlPlaneApi local, @Runtime(REMOTE_CP) ControlPlaneApi remote) {
        httpPullTransfer(protocol, path, local, remote);
    }

    private void httpPullTransfer(String protocol, String path, ControlPlaneApi consumer, ControlPlaneApi provider) {
        consumer.setProtocol(protocol, path);
        provider.setProtocol(protocol, path);
        provider.waitForDataPlane();
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


    }

    @ParameterizedTest
    @ArgumentsSource(ParticipantsArgProvider.class)
    void httpPullTransfer_suspendAndResume_whenConsumerLocal(String protocol, String path, @Runtime(LOCAL_CP) ControlPlaneApi local, @Runtime(REMOTE_CP) ControlPlaneApi remote) {
        httpPullTransfer_suspendAndResume(protocol, path, local, remote);
    }

    @ParameterizedTest
    @ArgumentsSource(ParticipantsArgProvider.class)
    void httpPullTransfer_suspendAndResume_whenConsumerRemote(String protocol, String path, @Runtime(LOCAL_CP) ControlPlaneApi local, @Runtime(REMOTE_CP) ControlPlaneApi remote) {
        httpPullTransfer_suspendAndResume(protocol, path, remote, local);
    }

    private void httpPullTransfer_suspendAndResume(String protocol, String path, ControlPlaneApi consumer, ControlPlaneApi provider) {
        consumer.setProtocol(protocol, path);
        provider.setProtocol(protocol, path);
        provider.waitForDataPlane();
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
    }

    protected void createResourcesOnProvider(ControlPlaneApi provider, String assetId, JsonObject contractPolicy, Map<String, Object> dataAddressProperties) {
        provider.createAsset(assetId, Map.of("description", "description"), dataAddressProperties);
        var contractPolicyId = provider.createPolicyDefinition(contractPolicy);
        var noConstraintPolicyId = provider.createPolicyDefinition(noConstraintPolicy());

        provider.createContractDefinition(assetId, UUID.randomUUID().toString(), noConstraintPolicyId, contractPolicyId);
    }

    private static class ParticipantsArgProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    Arguments.of("dataspace-protocol-http", ""),
                    Arguments.of("dataspace-protocol-http:2024/1", "/2024/1")
            );
        }
    }

}
