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
import org.eclipse.edc.identityhub.tests.fixtures.common.Named;
import org.eclipse.edc.identityhub.tests.fixtures.credentialservice.IdentityHubApiClient;
import org.eclipse.edc.identityhub.tests.fixtures.credentialservice.IdentityHubExtension;
import org.eclipse.edc.identityhub.tests.fixtures.credentialservice.IdentityHubRuntime;
import org.eclipse.edc.identityhub.tests.fixtures.issuerservice.IssuerExtension;
import org.eclipse.edc.identityhub.tests.fixtures.issuerservice.IssuerRuntime;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.sql.testfixtures.PostgresqlEndToEndExtension;
import org.eclipse.edc.tests.fixtures.Runtimes;
import org.eclipse.edc.tests.fixtures.extension.cp.ControlPlaneApi;
import org.eclipse.edc.tests.fixtures.extension.cp.ControlPlaneExtension;
import org.eclipse.edc.tests.fixtures.extension.cp.ControlPlaneRuntime;
import org.eclipse.edc.tests.fixtures.extension.dp.DataPlaneExtension;
import org.eclipse.edc.tests.fixtures.extension.dp.DataPlaneRuntime;
import org.eclipse.edc.tests.fixtures.transfer.HttpProxyDataPlaneExtension;
import org.jetbrains.annotations.NotNull;
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
import static org.eclipse.edc.dcp.tests.transfer.fixtures.TestFunction.setupHolder;
import static org.eclipse.edc.dcp.tests.transfer.fixtures.TestFunction.setupIssuer;
import static org.eclipse.edc.dcp.tests.transfer.fixtures.TestFunction.setupParticipant;
import static org.eclipse.edc.junit.testfixtures.TestUtils.getResourceFileContentAsString;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;

public class DcpTransferEndToEndTest {

    public static final String CONSUMER_IH = "consumer_ih";
    public static final String CONSUMER_CP = "consumer-cp";
    public static final String CONSUMER_DP = "consumer-dp";
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
    static final IssuerExtension ISSUER_EXTENSION = IssuerExtension.Builder.newInstance()
            .id(Runtimes.Issuer.ID)
            .name(Runtimes.Issuer.ID)
            .modules(Runtimes.Issuer.MODULES)
            .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(Runtimes.Issuer.ID))
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
    @Order(2)
    @RegisterExtension
    static final IdentityHubExtension CONSUMER_IH_EXTENSION = IdentityHubExtension.Builder.newInstance()
            .id("consumer-ih")
            .name(CONSUMER_IH)
            .modules(Runtimes.IdentityHub.MODULES)
            .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(CONSUMER_IH))
            .build();
    @Order(3)
    @RegisterExtension
    static final ControlPlaneExtension CONSUMER_CONTROL_PLANE_EXTENSION = ControlPlaneExtension.Builder.newInstance()
            .id(CONSUMER_IH_EXTENSION.didFor(CONSUMER_ID))
            .name(CONSUMER_CP)
            .modules(Runtimes.ControlPlane.DCP_MODULES)
            .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(CONSUMER_ID))
            .configurationProvider(CONSUMER_IH_EXTENSION::stsConfig)
            .configurationProvider(() -> dcpConfig(CONSUMER_IH_EXTENSION, CONSUMER_ID))
            .build();

    @Order(4)
    @RegisterExtension
    static final DataPlaneExtension CONSUMER_DATA_PLANE_EXTENSION = DataPlaneExtension.Builder.newInstance()
            .id(CONSUMER_DP)
            .name(CONSUMER_DP)
            .modules(Runtimes.DataPlane.MODULES)
            .configurationProvider(() -> dataPlaneSelectorFor(CONSUMER_CONTROL_PLANE_EXTENSION))
            .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(CONSUMER_ID))
            .build();

    @Order(2)
    @RegisterExtension
    static final IdentityHubExtension PROVIDER_IH_EXTENSION = IdentityHubExtension.Builder.newInstance()
            .id("provider-ih")
            .name(PROVIDER_IH)
            .modules(Runtimes.IdentityHub.MODULES)
            .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(PROVIDER_IH))
            .build();

    @Order(3)
    @RegisterExtension
    static final ControlPlaneExtension PROVIDER_CONTROL_PLANE_EXTENSION = ControlPlaneExtension.Builder.newInstance()
            .id(PROVIDER_IH_EXTENSION.didFor(PROVIDER_ID))
            .name(PROVIDER_CP)
            .modules(Runtimes.ControlPlane.DCP_MODULES)
            .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(PROVIDER_ID))
            .configurationProvider(PROVIDER_IH_EXTENSION::stsConfig)
            .configurationProvider(() -> dcpConfig(PROVIDER_IH_EXTENSION, PROVIDER_ID))
            .build();

    @Order(4)
    @RegisterExtension
    static final DataPlaneExtension PROVIDER_DATA_PLANE_EXTENSION = DataPlaneExtension.Builder.newInstance()
            .id(PROVIDER_DP)
            .name(PROVIDER_DP)
            .modules(Runtimes.DataPlane.MODULES)
            .configurationProvider(() -> dataPlaneSelectorFor(PROVIDER_CONTROL_PLANE_EXTENSION))
            .configurationProvider(() -> POSTGRESQL_EXTENSION.configFor(PROVIDER_ID))
            .build();

    protected static String privateKey = getResourceFileContentAsString("certs/key.pem");
    protected static String publicKey = getResourceFileContentAsString("certs/cert.pem");

    static {
        PROVIDER_CONTROL_PLANE_EXTENSION.registerSystemExtension(ServiceExtension.class, new DcpPatchExtension());
        PROVIDER_DATA_PLANE_EXTENSION.registerSystemExtension(ServiceExtension.class, new HttpProxyDataPlaneExtension());
        CONSUMER_CONTROL_PLANE_EXTENSION.registerSystemExtension(ServiceExtension.class, new DcpPatchExtension());
    }

    private static @NotNull Map<String, Object> httpSourceDataAddress() {
        return Map.of(
                EDC_NAMESPACE + "name", "transfer-test",
                EDC_NAMESPACE + "baseUrl", "http://anysource.com",
                EDC_NAMESPACE + "type", "HttpData",
                EDC_NAMESPACE + "proxyQueryParams", "true"
        );
    }

    /**
     * Set up the test environment by creating one issuer, two participants in their
     * respective Identity Hubs, and issuing a MembershipCredential credential for each participant.
     */
    @BeforeAll
    static void setup(IssuerRuntime issuerRuntime,
                      @Named(CONSUMER_IH) IdentityHubRuntime consumerRuntime,
                      @Named(PROVIDER_IH) IdentityHubRuntime providerRuntime,
                      @Named(CONSUMER_IH) IdentityHubApiClient consumerApiClient,
                      @Named(PROVIDER_IH) IdentityHubApiClient providerApiClient,
                      @Named(CONSUMER_CP) ControlPlaneRuntime consumerControlPlane,
                      @Named(PROVIDER_CP) ControlPlaneRuntime providerControlPlane,
                      @Named(PROVIDER_DP) DataPlaneRuntime providerDataPlane) {

        var vault = providerDataPlane.getService(Vault.class);
        vault.storeSecret("private-key", privateKey);
        vault.storeSecret("public-key", publicKey);

        var issuerDid = issuerRuntime.didFor(Runtimes.Issuer.ID);

        setupIssuer(issuerRuntime, Runtimes.Issuer.ID, issuerDid);

        setupHolder(issuerRuntime, Runtimes.Issuer.ID, providerControlPlane);
        setupHolder(issuerRuntime, Runtimes.Issuer.ID, consumerControlPlane);

        var providerResponse = setupParticipant(providerRuntime, providerControlPlane);
        var consumerResponse = setupParticipant(consumerRuntime, consumerControlPlane);

        var providerPid = providerApiClient.requestCredential(providerResponse.apiKey(), providerControlPlane.getId(), issuerDid, "MembershipCredential");
        var consumerPid = consumerApiClient.requestCredential(consumerResponse.apiKey(), consumerControlPlane.getId(), issuerDid, "MembershipCredential");

        providerRuntime.waitForCredentialIssuer(providerPid, providerControlPlane.getId());
        consumerRuntime.waitForCredentialIssuer(consumerPid, consumerControlPlane.getId());

    }

    private static Config dcpConfig(IdentityHubExtension extension, String id) {
        var did = extension.didFor(id);
        return ConfigFactory.fromMap(Map.of(
                "edc.iam.issuer.id", did,
                "edc.iam.sts.oauth.client.id", did,
                "edc.iam.sts.oauth.client.secret.alias", did + "-alias",
                "edc.iam.did.web.use.https", "false"
        ));
    }

    private static Config dataPlaneSelectorFor(ControlPlaneExtension extension) {
        return ConfigFactory.fromMap(Map.of(
                "edc.dpf.selector.url", extension.getControlPlaneControl().getUrl() + "/v1/dataplanes"
        ));
    }

    @Test
    void httpPullTransfer(@Named(CONSUMER_CP) ControlPlaneApi consumer,
                          @Named(PROVIDER_CP) ControlPlaneApi provider) {

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