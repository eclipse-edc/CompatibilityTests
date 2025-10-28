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

package org.eclipse.edc.dcp.tests.transfer;

import jakarta.json.JsonObject;
import org.eclipse.edc.connector.controlplane.test.system.utils.PolicyFixtures;
import org.eclipse.edc.dcp.tests.extensions.DcpPatchExtension;
import org.eclipse.edc.identityhub.tests.fixtures.DefaultRuntimes;
import org.eclipse.edc.identityhub.tests.fixtures.credentialservice.IdentityHub;
import org.eclipse.edc.identityhub.tests.fixtures.credentialservice.IdentityHubApiClient;
import org.eclipse.edc.identityhub.tests.fixtures.issuerservice.IssuerService;
import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.junit.annotations.Runtime;
import org.eclipse.edc.junit.extensions.ComponentRuntimeContext;
import org.eclipse.edc.junit.extensions.ComponentRuntimeExtension;
import org.eclipse.edc.junit.extensions.RuntimeExtension;
import org.eclipse.edc.junit.utils.Endpoints;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.sql.testfixtures.PostgresqlEndToEndExtension;
import org.eclipse.edc.tests.fixtures.Runtimes;
import org.eclipse.edc.tests.fixtures.extension.cp.ControlPlaneApi;
import org.eclipse.edc.tests.fixtures.transfer.HttpProxyDataPlaneExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.connector.controlplane.test.system.utils.PolicyFixtures.noConstraintPolicy;
import static org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates.STARTED;
import static org.eclipse.edc.dcp.tests.transfer.fixtures.TestFunction.httpSourceDataAddress;
import static org.eclipse.edc.dcp.tests.transfer.fixtures.TestFunction.setupHolder;
import static org.eclipse.edc.dcp.tests.transfer.fixtures.TestFunction.setupIssuer;
import static org.eclipse.edc.dcp.tests.transfer.fixtures.TestFunction.setupParticipant;
import static org.eclipse.edc.junit.testfixtures.TestUtils.getResourceFileContentAsString;

@EndToEndTest
public class DcpTransferEndToEndTest {

    public static final String CONSUMER_IH = "consumer_ih";
    public static final String CONSUMER_CP = "consumer-cp";
    public static final String PROVIDER_IH = "provider_ih";
    public static final String CONSUMER_ID = "consumer";
    public static final String PROVIDER_ID = "provider";
    public static final String PROVIDER_CP = "provider-cp";
    public static final String PROVIDER_DP = "provider-dp";

    @Order(0)
    @RegisterExtension
    static final PostgresqlEndToEndExtension POSTGRESQL_EXTENSION = new PostgresqlEndToEndExtension();

    @Order(2)
    @RegisterExtension
    static final RuntimeExtension ISSUER_EXTENSION = ComponentRuntimeExtension.Builder.newInstance()
            .name(Runtimes.Issuer.ID)
            .modules(Runtimes.Issuer.MODULES)
            .endpoints(DefaultRuntimes.Issuer.ENDPOINTS.build())
            .configurationProvider(DefaultRuntimes.Issuer::config)
            .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(Runtimes.Issuer.ID))
            .paramProvider(IssuerService.class, IssuerService::forContext)
            .build();

    @Order(1)
    @RegisterExtension
    static final BeforeAllCallback POSTGRES_CONTAINER_STARTER = context -> {
        POSTGRESQL_EXTENSION.createDatabase(Runtimes.Issuer.ID);
        POSTGRESQL_EXTENSION.createDatabase(CONSUMER_IH);
        POSTGRESQL_EXTENSION.createDatabase(PROVIDER_IH);
        POSTGRESQL_EXTENSION.createDatabase(CONSUMER_ID);
        POSTGRESQL_EXTENSION.createDatabase(PROVIDER_ID);
    };

    static final Endpoints CONSUMER_IH_ENDPOINTS = DefaultRuntimes.IdentityHub.ENDPOINTS.build();

    @Order(2)
    @RegisterExtension
    static final ComponentRuntimeExtension CONSUMER_IH_EXTENSION = ComponentRuntimeExtension.Builder.newInstance()
            .name(CONSUMER_IH)
            .modules(Runtimes.IdentityHub.MODULES)
            .endpoints(CONSUMER_IH_ENDPOINTS)
            .configurationProvider(DefaultRuntimes.IdentityHub::config)
            .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(CONSUMER_IH))
            .configurationProvider(() -> ConfigFactory.fromMap(Map.of("edc.iam.credential.status.check.period", "0")))
            .paramProvider(IdentityHub.class, IdentityHub::forContext)
            .paramProvider(IdentityHubApiClient.class, IdentityHubApiClient::forContext)
            .build();

    @Order(3)
    @RegisterExtension
    static final RuntimeExtension CONSUMER_CP_EXTENSION = ComponentRuntimeExtension.Builder.newInstance()
            .name(CONSUMER_CP)
            .modules(Runtimes.ControlPlane.DCP_MODULES)
            .endpoints(Runtimes.ControlPlane.ENDPOINTS.build())
            .configurationProvider(() -> Runtimes.IdentityHub.dcpConfig(CONSUMER_IH_ENDPOINTS, CONSUMER_ID))
            .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(CONSUMER_ID))
            .paramProvider(ControlPlaneApi.class, ControlPlaneApi::forContext)
            .build()
            .registerSystemExtension(ServiceExtension.class, new DcpPatchExtension());

    static final Endpoints PROVIDER_IH_ENDPOINTS = DefaultRuntimes.IdentityHub.ENDPOINTS.build();

    @Order(2)
    @RegisterExtension
    static final RuntimeExtension PROVIDER_IH_EXTENSION = ComponentRuntimeExtension.Builder.newInstance()
            .name(PROVIDER_IH)
            .modules(Runtimes.IdentityHub.MODULES)
            .endpoints(PROVIDER_IH_ENDPOINTS)
            .configurationProvider(DefaultRuntimes.IdentityHub::config)
            .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(PROVIDER_IH))
            .configurationProvider(() -> ConfigFactory.fromMap(Map.of("edc.iam.credential.status.check.period", "0")))
            .paramProvider(IdentityHub.class, IdentityHub::forContext)
            .paramProvider(IdentityHubApiClient.class, IdentityHubApiClient::forContext)
            .build();

    static final Endpoints PROVIDER_CP_ENDPOINTS = Runtimes.ControlPlane.ENDPOINTS.build();

    @Order(3)
    @RegisterExtension
    static final RuntimeExtension PROVIDER_CP_EXTENSION = ComponentRuntimeExtension.Builder.newInstance()
            .name(PROVIDER_CP)
            .modules(Runtimes.ControlPlane.DCP_MODULES)
            .endpoints(PROVIDER_CP_ENDPOINTS)
            .configurationProvider(() -> Runtimes.IdentityHub.dcpConfig(PROVIDER_IH_ENDPOINTS, PROVIDER_ID))
            .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(PROVIDER_ID))
            .paramProvider(ControlPlaneApi.class, ControlPlaneApi::forContext)
            .build()
            .registerSystemExtension(ServiceExtension.class, new DcpPatchExtension());


    @Order(4)
    @RegisterExtension
    static final RuntimeExtension PROVIDER_DP_EXTENSION = ComponentRuntimeExtension.Builder.newInstance()
            .name(PROVIDER_DP)
            .modules(Runtimes.DataPlane.MODULES)
            .endpoints(Runtimes.DataPlane.ENDPOINTS.build())
            .configurationProvider(Runtimes.DataPlane::config)
            .configurationProvider(() -> Runtimes.ControlPlane.dataPlaneSelectorFor(PROVIDER_CP_ENDPOINTS))
            .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(PROVIDER_ID))
            .build()
            .registerSystemExtension(ServiceExtension.class, new HttpProxyDataPlaneExtension());

    /**
     * Set up the test environment by creating one issuer, two participants in their
     * respective Identity Hubs, and issuing a MembershipCredential credential for each participant.
     */
    @BeforeAll
    static void setup(IssuerService issuer,
                      @Runtime(CONSUMER_IH) IdentityHub consumerIdentityHub,
                      @Runtime(PROVIDER_IH) IdentityHub providerIdentityHub,
                      @Runtime(CONSUMER_IH) IdentityHubApiClient consumerApiClient,
                      @Runtime(PROVIDER_IH) IdentityHubApiClient providerApiClient,
                      @Runtime(CONSUMER_CP) ComponentRuntimeContext consumerCtx,
                      @Runtime(PROVIDER_CP) ComponentRuntimeContext providerCtx,
                      @Runtime(PROVIDER_DP) Vault dpVault) {

        var privateKey = getResourceFileContentAsString("certs/key.pem");
        var publicKey = getResourceFileContentAsString("certs/cert.pem");


        var consumerHolderDid = consumerIdentityHub.didFor(CONSUMER_ID);
        var providerHolderDid = providerIdentityHub.didFor(PROVIDER_ID);
        dpVault.storeSecret("private-key", privateKey);
        dpVault.storeSecret("public-key", publicKey);


        var issuerDid = issuer.didFor(Runtimes.Issuer.ID);

        setupIssuer(issuer, Runtimes.Issuer.ID, issuerDid);

        setupHolder(issuer, Runtimes.Issuer.ID, consumerHolderDid);
        setupHolder(issuer, Runtimes.Issuer.ID, providerHolderDid);

        var providerResponse = setupParticipant(providerIdentityHub, providerCtx, issuerDid, providerHolderDid);
        var consumerResponse = setupParticipant(consumerIdentityHub, consumerCtx, issuerDid, consumerHolderDid);


        var providerPid = providerApiClient.requestCredential(providerResponse.apiKey(), providerHolderDid, issuerDid, "credential-id", "MembershipCredential");
        var consumerPid = consumerApiClient.requestCredential(consumerResponse.apiKey(), consumerHolderDid, issuerDid, "credential-id", "MembershipCredential");

        providerIdentityHub.waitForCredentialIssuer(providerPid, providerHolderDid);
        consumerIdentityHub.waitForCredentialIssuer(consumerPid, consumerHolderDid);

    }

    @Test
    void httpPullTransfer(@Runtime(CONSUMER_CP) ControlPlaneApi consumer,
                          @Runtime(PROVIDER_CP) ControlPlaneApi provider) {

        provider.waitForDataPlane();

        var assetId = UUID.randomUUID().toString();
        var sourceDataAddress = httpSourceDataAddress();
        createResourcesOnProvider(provider, assetId, PolicyFixtures.contractExpiresIn("5s"), sourceDataAddress);

        var transferProcessId = consumer.requestAssetFrom(assetId, provider)
                .withTransferType("HttpData-PULL")
                .execute();

        consumer.awaitTransferToBeInState(transferProcessId, STARTED);


        var edr = await().atMost(consumer.getTimeout()).until(() -> consumer.getEdr(transferProcessId), Objects::nonNull);

        var msg = UUID.randomUUID().toString();
        await().atMost(consumer.getTimeout()).untilAsserted(() -> consumer.pullData(edr, Map.of("message", msg), body -> assertThat(body).isEqualTo("data")));
    }

    protected void createResourcesOnProvider(ControlPlaneApi provider, String assetId, JsonObject contractPolicy, Map<String, Object> dataAddressProperties) {
        provider.createAsset(assetId, Map.of("description", "description"), dataAddressProperties);
        var contractPolicyId = provider.createPolicyDefinition(contractPolicy);
        var noConstraintPolicyId = provider.createPolicyDefinition(noConstraintPolicy());

        provider.createContractDefinition(assetId, UUID.randomUUID().toString(), noConstraintPolicyId, contractPolicyId);
    }

}