/*
 *  Copyright (c) 2025 SAP SE
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       SAP SE - initial API and implementation
 *
 */
package org.eclipse.edc.compatibility.tests;

import org.eclipse.edc.compatibility.tests.fixtures.BaseParticipant;
import org.eclipse.edc.compatibility.tests.fixtures.EdcDockerRuntimes;
import org.eclipse.edc.compatibility.tests.fixtures.LocalParticipant;
import org.eclipse.edc.compatibility.tests.fixtures.RemoteParticipant;
import org.eclipse.edc.compatibility.tests.fixtures.Runtimes;
import org.eclipse.edc.connector.controlplane.test.system.utils.Participant;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimePerClassExtension;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.sql.testfixtures.PostgresqlEndToEndInstance;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.sql.testfixtures.PostgresqlEndToEndInstance.createDatabase;
import static org.eclipse.edc.util.io.Ports.getFreePort;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;

public abstract class AbstractTest {
    private static final List<String> PROTOCOLS_TO_TEST = List.of("dataspace-protocol-http", "dataspace-protocol-http:2024/1");
    private static final LocalParticipant LOCAL_PARTICIPANT = LocalParticipant.Builder.newInstance().name("local").id("local").build();
    private static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.4").withUsername("postgres").withPassword("password").withCreateContainerCmdModifier(cmd -> cmd.withName("postgres"));
    protected static List<Participant> participants = new ArrayList<>();
    protected ClientAndServer providerDataSource = startClientAndServer(getFreePort());

    @BeforeAll
    static void beforeAll() {

    }

    @BeforeEach
    void beforeEach() {
        providerDataSource.reset();
    }

    @Order(0)
    @RegisterExtension
    static final BeforeAllCallback INIT_DATABASE = context -> {
        PG.setPortBindings(List.of("5432:5432"));
        PG.start();
        createDatabase(LOCAL_PARTICIPANT.getName());

    };
    @Order(1)
    @RegisterExtension
    static final BeforeAllCallback INIT_CONTAINERS = context -> {
        participants.addAll(List.of(
                LOCAL_PARTICIPANT,
                createRemoteParticipant(EdcDockerRuntimes.STABLE_CONNECTOR, PostgresqlEndToEndInstance::createDatabase),
                createRemoteParticipant(EdcDockerRuntimes.STABLE_CONNECTOR_0_10_0, PostgresqlEndToEndInstance::createDatabase)
        ));
    };

    private static RemoteParticipant createRemoteParticipant(EdcDockerRuntimes runtime, Consumer<String> callback) {
        var remoteParticipant = RemoteParticipant.Builder.newInstance().name(runtime.name().toLowerCase()).id(runtime.name().toLowerCase()).build();
        callback.accept(runtime.name().toLowerCase());
        runtime.start(remoteParticipant.controlPlaneEnv(), remoteParticipant.dataPlaneEnv());
        return remoteParticipant;
    }

    @Order(2)
    @RegisterExtension
    protected static final RuntimeExtension LOCAL_CONTROL_PLANE = new RuntimePerClassExtension(Runtimes.CONTROL_PLANE.create("local-control-plane", LOCAL_PARTICIPANT.controlPlanePostgresConfiguration()));

    @Order(3)
    @RegisterExtension
    protected static final RuntimeExtension LOCAL_DATA_PLANE = new RuntimePerClassExtension(Runtimes.DATA_PLANE.create("local-data-plane", LOCAL_PARTICIPANT.dataPlanePostgresConfiguration()));


    @BeforeAll
    static void storeKeys() {
        var vault = LOCAL_DATA_PLANE.getService(Vault.class);
        vault.storeSecret("private-key", LOCAL_PARTICIPANT.getPrivateKey());
        vault.storeSecret("public-key", LOCAL_PARTICIPANT.getPublicKey());
    }

    protected void initialise(BaseParticipant consumer, BaseParticipant provider) {
        initialise(consumer, provider, "dataspace-protocol-http");
    }

    protected void initialise(BaseParticipant consumer, BaseParticipant provider, String protocol) {
        provider.setProtocol(protocol);
        consumer.setProtocol(protocol);
        provider.waitForDataPlane();
        providerDataSource.when(HttpRequest.request()).respond(HttpResponse.response("/source").withBody("data"));
    }

    protected Map<String, Object> createDataAddress(ClientAndServer server, String dataAddressPath) {
        return Map.of(EDC_NAMESPACE + "name", "testing", EDC_NAMESPACE + "baseUrl", "http://localhost:" + server.getPort() + dataAddressPath, EDC_NAMESPACE + "type", "HttpData", EDC_NAMESPACE + "proxyQueryParams", "true");
    }

    protected static class SuspendResumeTransferByProviderArgs implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            Predicate<Participant> filterConsumer = p -> !EdcDockerRuntimes.STABLE_CONNECTOR_0_10_0.name().toLowerCase().equals(p.getName());
            return createArgumentMatrix(filterConsumer, p -> Boolean.TRUE).stream();
        }
    }

    protected static class ParticipantsArgProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return createArgumentMatrix().stream();
        }

    }

    private static List<Arguments> createArgumentMatrix(Predicate<Participant> canBeConsumer, Predicate<Participant> canBeProvider) {
        List<Arguments> testArguments = new ArrayList<>();
        for (int i = 0; i < participants.size(); i++) {
            Participant consumer = participants.get(i);
            for (int j = i + 1; j < participants.size(); j++) {
                Participant provider = participants.get(j);
                for (String protocol : PROTOCOLS_TO_TEST) {
                    if (canBeConsumer.test(consumer) && canBeProvider.test(provider)) {
                        testArguments.add(Arguments.of(Named.of(consumer.getName(), consumer), Named.of(provider.getName(), provider), protocol));
                    }
                    if (canBeConsumer.test(provider) && canBeProvider.test(consumer)) {
                        testArguments.add(Arguments.of(Named.of(provider.getName(), provider), Named.of(consumer.getName(), consumer), protocol));
                    }
                }
            }
        }
        return testArguments;
    }

    private static List<Arguments> createArgumentMatrix() {
        return createArgumentMatrix(p -> Boolean.TRUE, p -> Boolean.TRUE);
    }

}
