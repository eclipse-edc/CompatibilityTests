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

package org.eclipse.edc.dcp.tests.transfer.fixtures;

import org.eclipse.edc.iam.verifiablecredentials.spi.RevocationListService;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialStatus;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.RevocationServiceRegistry;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.iam.verifiablecredentials.spi.validation.TrustedIssuerRegistry;
import org.eclipse.edc.identityhub.spi.participantcontext.model.CreateParticipantContextResponse;
import org.eclipse.edc.identityhub.tests.fixtures.credentialservice.IdentityHubRuntime;
import org.eclipse.edc.identityhub.tests.fixtures.issuerservice.IssuerRuntime;
import org.eclipse.edc.issuerservice.spi.issuance.attestation.AttestationDefinitionService;
import org.eclipse.edc.issuerservice.spi.issuance.credentialdefinition.CredentialDefinitionService;
import org.eclipse.edc.issuerservice.spi.issuance.model.AttestationDefinition;
import org.eclipse.edc.issuerservice.spi.issuance.model.CredentialDefinition;
import org.eclipse.edc.issuerservice.spi.issuance.model.CredentialRuleDefinition;
import org.eclipse.edc.issuerservice.spi.issuance.model.MappingDefinition;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.tests.fixtures.extension.cp.ControlPlaneRuntime;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;

import java.sql.SQLException;
import java.util.Map;

import static org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialFormat.VC1_0_JWT;

public class TestFunction {


    public static void setupIssuer(IssuerRuntime issuerRuntime, String participantContextId, String did) {
        issuerRuntime.createParticipant(participantContextId, did, did + "#key");
        var attestationDefinitionService = issuerRuntime.getService(AttestationDefinitionService.class);
        var credentialDefinitionService = issuerRuntime.getService(CredentialDefinitionService.class);
        var dataSourceRegistry = issuerRuntime.getService(DataSourceRegistry.class);
        var executor = issuerRuntime.getService(QueryExecutor.class);

        var attestationDefinition = AttestationDefinition.Builder.newInstance().id("attestation-id")
                .attestationType("database")
                .participantContextId(participantContextId)
                .configuration(Map.of(
                        "dataSourceName", "default",
                        "tableName", "attestations",
                        "idColumn", "holderId"))
                .build();

        attestationDefinitionService.createAttestation(attestationDefinition)
                .orElseThrow(f -> new RuntimeException(f.getFailureDetail()));


        Map<String, Object> ruleConfiguration = Map.of(
                "claim", "member_signed_document",
                "operator", "eq",
                "value", "t");

        var credentialDefinition = CredentialDefinition.Builder.newInstance()
                .id("credential-id")
                .credentialType("MembershipCredential")
                .jsonSchemaUrl("https://example.com/schema")
                .jsonSchema("{}")
                .attestation(attestationDefinition.getId())
                .validity(3600)
                .mapping(new MappingDefinition("member_name", "credentialSubject.name", true))
                .mapping(new MappingDefinition("membership_start_date", "credentialSubject.membershipStartData", true))
                .rule(new CredentialRuleDefinition("expression", ruleConfiguration))
                .participantContextId(participantContextId)
                .formatFrom(VC1_0_JWT)
                .build();

        credentialDefinitionService.createCredentialDefinition(credentialDefinition)
                .orElseThrow(f -> new RuntimeException(f.getFailureDetail()));


        var dataSource = dataSourceRegistry.resolve("default");
        var tx = issuerRuntime.getService(TransactionContext.class);

        tx.execute(() -> {
            try (var connection = dataSource.getConnection()) {
                executor.execute(connection, "CREATE TABLE attestations (holderId VARCHAR(255), member_name VARCHAR(255), membership_start_date timestamp, member_signed_document BOOLEAN)");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });

    }

    public static void setupHolder(IssuerRuntime issuerRuntime, String participantContextId, ControlPlaneRuntime controlPlaneRuntime) {

        issuerRuntime.createHolder(participantContextId, controlPlaneRuntime.getId(), controlPlaneRuntime.getId(), controlPlaneRuntime.getName());

        var dataSourceRegistry = issuerRuntime.getService(DataSourceRegistry.class);
        var tx = issuerRuntime.getService(TransactionContext.class);
        var executor = issuerRuntime.getService(QueryExecutor.class);


        controlPlaneRuntime.getService(TrustedIssuerRegistry.class).register(new Issuer(issuerRuntime.didFor(participantContextId), Map.of()), "*");

        tx.execute(() -> {
            var dataSource = dataSourceRegistry.resolve("default");

            try (var connection = dataSource.getConnection()) {
                executor.execute(connection, "INSERT INTO attestations (holderId, member_name, membership_start_date, member_signed_document) VALUES (?, ?, now(), true)", controlPlaneRuntime.getId(), controlPlaneRuntime.getName());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });

    }


    public static CreateParticipantContextResponse setupParticipant(IdentityHubRuntime identityHubRuntime, ControlPlaneRuntime controlPlaneRuntime) {
        var participantId = controlPlaneRuntime.getId();
        var response = identityHubRuntime.createParticipant(participantId, participantId, participantId + "#key");

        var vault = controlPlaneRuntime.getService(Vault.class);
        vault.storeSecret(participantId + "-alias", response.clientSecret());

        var revocationRegistry = controlPlaneRuntime.getService(RevocationServiceRegistry.class);

        revocationRegistry.addService("BitstringStatusListEntry", new RevocationListService() {
            @Override
            public Result<Void> checkValidity(CredentialStatus credentialStatus) {
                return Result.success();
            }

            @Override
            public Result<String> getStatusPurpose(VerifiableCredential verifiableCredential) {
                return null;
            }
        });
        return response;
    }
}
