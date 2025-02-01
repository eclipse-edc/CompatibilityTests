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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import org.eclipse.edc.compatibility.tests.AbstractTest;
import org.eclipse.edc.compatibility.tests.fixtures.BaseParticipant;
import org.eclipse.edc.connector.controlplane.test.system.utils.PolicyFixtures;
import org.eclipse.edc.connector.controlplane.transfer.spi.event.TransferProcessStarted;
import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.spi.event.EventEnvelope;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.MediaType;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.DEPROVISIONED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.STARTED;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.SUSPENDED;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.util.io.Ports.getFreePort;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@EndToEndTest
class TransferEndToEndTest extends AbstractTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ParameterizedTest
    @ArgumentsSource(ParticipantsArgProvider.class)
    void httpPullTransferWithExpiry(BaseParticipant consumer, BaseParticipant provider, String protocol) {
        initialise(consumer, provider, protocol);
        var assetId = provider.createResource(createDataAddress(providerDataSource, "/source"), PolicyFixtures.contractExpiresIn("5s"));
        var transferProcessId = consumer.requestAssetFrom(assetId, provider).withTransferType("HttpData-PULL").execute();
        consumer.awaitTransferToBeInState(transferProcessId, STARTED);
        var edr = await().atMost(consumer.getTimeout()).until(() -> consumer.getEdr(transferProcessId), Objects::nonNull);
        // Do the transfer
        var msg = UUID.randomUUID().toString();
        await().atMost(consumer.getTimeout()).untilAsserted(() -> consumer.pullData(edr, Map.of("message", msg), body -> assertThat(body).isEqualTo("data")));
        // checks that the EDR is gone once the contract expires
        await().atMost(consumer.getTimeout()).untilAsserted(() -> assertThatThrownBy(() -> consumer.getEdr(transferProcessId)));

        // checks that transfer fails
        await().atMost(consumer.getTimeout()).untilAsserted(() -> assertThatThrownBy(() -> consumer.pullData(edr, Map.of("message", msg), body -> assertThat(body).isEqualTo("data"))));

        providerDataSource.verify(HttpRequest.request("/source").withMethod("GET"));

    }

    @ParameterizedTest
    @ArgumentsSource(ParticipantsArgProvider.class)
    void suspendAndResume_httpPull_dataTransfer(BaseParticipant consumer, BaseParticipant provider, String protocol) {
        initialise(consumer, provider, protocol);
        Map<String, Object> dataAddress = createDataAddress(providerDataSource, "/source");
        var assetId = provider.createResource(dataAddress, PolicyFixtures.noConstraintPolicy());

        var transferProcessId = consumer.requestAssetFrom(assetId, provider).withTransferType("HttpData-PULL").execute();

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

        providerDataSource.verify(HttpRequest.request("/source").withMethod("GET"));
    }

    /**
     * This test is failing for version <= 0.10.1, hence it is disabled for those versions.
     */
    @ParameterizedTest
    @ArgumentsSource(SuspendResumeTransferByProviderArgs.class)
    void suspendAndResumeByProvider_httpPull_dataTransfer(BaseParticipant consumer, BaseParticipant provider, String protocol) {
        initialise(consumer, provider, protocol);
        Map<String, Object> dataAddress = createDataAddress(providerDataSource, "/source");
        var assetId = provider.createResource(dataAddress, PolicyFixtures.noConstraintPolicy());
        String consumerTransferProcessId = consumer.requestAssetFrom(assetId, provider).withTransferType("HttpData-PULL").execute();
        consumer.awaitTransferToBeInState(consumerTransferProcessId, STARTED);
        var edr = await().atMost(consumer.getTimeout()).until(() -> consumer.getEdr(consumerTransferProcessId), Objects::nonNull);
        var msg = UUID.randomUUID().toString();
        await().atMost(consumer.getTimeout()).untilAsserted(() -> consumer.pullData(edr, Map.of("message", msg), body -> assertThat(body).isEqualTo("data")));
        var providerTransferProcessId =
                provider.getTransferProcesses().stream().filter(filter -> filter.asJsonObject().getString("correlationId").equals(consumerTransferProcessId)).map(id -> id.asJsonObject().getString("@id")).findFirst().orElseThrow();
        provider.suspendTransfer(providerTransferProcessId, "suspension");
        provider.awaitTransferToBeInState(providerTransferProcessId, SUSPENDED);
        // checks that the EDR is gone once the transfer has been suspended
        await().atMost(consumer.getTimeout()).untilAsserted(() -> assertThatThrownBy(() -> consumer.getEdr(consumerTransferProcessId)));
        // checks that transfer fails
        await().atMost(consumer.getTimeout()).untilAsserted(() -> assertThatThrownBy(() -> consumer.pullData(edr, Map.of("message", msg), body -> assertThat(body).isEqualTo("data"))));
        provider.resumeTransfer(providerTransferProcessId);
        // check that transfer is available again
        provider.awaitTransferToBeInState(providerTransferProcessId, STARTED);
        var secondEdr = await().atMost(consumer.getTimeout()).until(() -> consumer.getEdr(consumerTransferProcessId), Objects::nonNull);
        var secondMessage = UUID.randomUUID().toString();
        await().atMost(consumer.getTimeout()).untilAsserted(() -> consumer.pullData(secondEdr, Map.of("message", secondMessage), body -> assertThat(body).isEqualTo("data")));
        providerDataSource.verify(HttpRequest.request("/source").withMethod("GET"));
    }


    @ParameterizedTest
    @ArgumentsSource(ParticipantsArgProvider.class)
    void terminateTransferProcess(BaseParticipant consumer, BaseParticipant provider, String protocol) {
        initialise(consumer, provider, protocol);
        Map<String, Object> dataAddress = createDataAddress(providerDataSource, "/source");
        var assetId = provider.createResource(dataAddress, PolicyFixtures.noConstraintPolicy());
        String transferProcessId = consumer.requestAssetFrom(assetId, provider).withTransferType("HttpData-PULL").execute();
        consumer.awaitTransferToBeInState(transferProcessId, STARTED);
        DataAddress edr = await().atMost(consumer.getTimeout()).until(() -> consumer.getEdr(transferProcessId), Objects::nonNull);
        await().atMost(consumer.getTimeout()).untilAsserted(() -> consumer.pullData(edr, Map.of(), body -> assertThat(body).isEqualTo("data")));
        var providerTransferProcessId = provider.getTransferProcesses().stream().filter(filter -> filter.asJsonObject().getString("correlationId").equals(transferProcessId)).map(id -> id.asJsonObject().getString("@id")).findFirst().orElseThrow();
        provider.terminateTransfer(providerTransferProcessId);
        provider.awaitTransferToBeInState(providerTransferProcessId, DEPROVISIONED);
        // checks that the EDR is gone once the transfer has been terminated
        await().atMost(consumer.getTimeout()).untilAsserted(() -> assertThatThrownBy(() -> consumer.getEdr(transferProcessId)));
        await().atMost(consumer.getTimeout()).untilAsserted(() -> assertThatThrownBy(() -> consumer.pullData(edr, Map.of(), body -> assertThat(body).isEqualTo("data"))));
        providerDataSource.verify(HttpRequest.request("/source").withMethod("GET"));
    }

    @ParameterizedTest
    @ArgumentsSource(ParticipantsArgProvider.class)
    void deprovisionShouldFailOnceTransferProcessHasStarted(BaseParticipant consumer, BaseParticipant provider, String protocol) {
        initialise(consumer, provider, protocol);
        Map<String, Object> dataAddress = createDataAddress(providerDataSource, "/source");
        var assetId = provider.createResource(dataAddress, PolicyFixtures.noConstraintPolicy());
        String transferProcessId = consumer.requestAssetFrom(assetId, provider).withTransferType("HttpData-PULL").execute();
        consumer.awaitTransferToBeInState(transferProcessId, STARTED);
        assertThatThrownBy(() -> consumer.deprovisionTransfer(transferProcessId)).hasMessageContaining("Expected status code <204> but was <409>");
        DataAddress edr = await().atMost(consumer.getTimeout()).until(() -> consumer.getEdr(transferProcessId), Objects::nonNull);
        await().atMost(consumer.getTimeout()).untilAsserted(() -> consumer.pullData(edr, Map.of(), body -> assertThat(body).isEqualTo("data")));
        providerDataSource.verify(HttpRequest.request("/source").withMethod("GET"));
    }

    @ParameterizedTest
    @ArgumentsSource(ParticipantsArgProvider.class)
    void httpPull_dataTransfer_withCallbacks(BaseParticipant consumer, BaseParticipant provider, String protocol) {
        initialise(consumer, provider, protocol);
        Map<String, Object> dataAddress = createDataAddress(providerDataSource, "/source");
        var callbacksEndpoint = startClientAndServer(getFreePort());
        var assetId = provider.createResource(dataAddress, PolicyFixtures.noConstraintPolicy());
        var callbackUrl = String.format("http://localhost:%d/hooks", callbacksEndpoint.getLocalPort());
        var callbacks = Json.createArrayBuilder().add(createCallback(callbackUrl, true, Set.of("transfer.process.started"))).build();

        var request = request().withPath("/hooks").withMethod(HttpMethod.POST.name());

        var events = new ConcurrentHashMap<String, TransferProcessStarted>();

        callbacksEndpoint.when(request).respond(req -> this.cacheEdr(req, events));

        var transferProcessId = consumer.requestAssetFrom(assetId, provider).withTransferType("HttpData-PULL").withCallbacks(callbacks).execute();

        consumer.awaitTransferToBeInState(transferProcessId, STARTED);

        await().atMost(consumer.getTimeout()).untilAsserted(() -> assertThat(events.get(transferProcessId)).isNotNull());
        var event = events.get(transferProcessId);
        var msg = UUID.randomUUID().toString();
        await().atMost(consumer.getTimeout()).untilAsserted(() -> consumer.pullData(event.getDataAddress(), Map.of("message", msg), body -> assertThat(body).isEqualTo("data")));

        providerDataSource.verify(request("/source").withMethod("GET"));
    }

    private JsonObject createCallback(String url, boolean transactional, Set<String> events) {
        return Json.createObjectBuilder().add(TYPE, EDC_NAMESPACE + "CallbackAddress").add(EDC_NAMESPACE + "transactional", transactional).add(EDC_NAMESPACE + "uri", url)
                .add(EDC_NAMESPACE + "events", events.stream().collect(Json::createArrayBuilder, JsonArrayBuilder::add, JsonArrayBuilder::add).build()).build();
    }

    private HttpResponse cacheEdr(HttpRequest request, Map<String, TransferProcessStarted> events) {

        try {
            var event = MAPPER.readValue(request.getBody().toString(), new TypeReference<EventEnvelope<TransferProcessStarted>>() {
            });
            events.put(event.getPayload().getTransferProcessId(), event.getPayload());
            return response().withStatusCode(HttpStatusCode.OK_200.code()).withHeader(HttpHeaderNames.CONTENT_TYPE.toString(), MediaType.PLAIN_TEXT_UTF_8.toString()).withBody("{}");

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
