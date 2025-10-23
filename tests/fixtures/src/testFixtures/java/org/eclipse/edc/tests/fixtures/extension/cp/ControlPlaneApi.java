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

package org.eclipse.edc.tests.fixtures.extension.cp;

import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowingConsumer;
import org.eclipse.edc.connector.controlplane.test.system.utils.Participant;
import org.eclipse.edc.junit.extensions.ComponentRuntimeContext;
import org.eclipse.edc.junit.utils.LazySupplier;
import org.eclipse.edc.spi.types.domain.DataAddress;

import java.net.URI;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.web.spi.configuration.ApiContext.MANAGEMENT;
import static org.eclipse.edc.web.spi.configuration.ApiContext.PROTOCOL;

public class ControlPlaneApi extends Participant {

    public static final String API_KEY = "password";

    public static ControlPlaneApi forContext(ComponentRuntimeContext ctx) {
        var id = ctx.getConfig().getString("edc.participant.id");
        return ControlPlaneApi.Builder.newInstance()
                .id(id)
                .name("name")
                .managementUrl(ctx.getEndpoint(MANAGEMENT))
                .protocolUrl(ctx.getEndpoint(PROTOCOL))
                .build();
    }

    public void waitForDataPlane() {
        await().atMost(timeout)
                .untilAsserted(() -> {
                    var jp = baseManagementRequest()
                            .get("/v3/dataplanes")
                            .then()
                            .statusCode(200)
                            .log().ifValidationFails()
                            .extract().body().jsonPath();

                    var state = jp.getString("state");
                    Assertions.assertThat(state).isEqualTo("[AVAILABLE]");
                });
    }

    /**
     * Get the EDR from the EDR cache by transfer process id.
     *
     * @param transferProcessId The transfer process id
     * @return The cached {@link DataAddress}
     */
    public DataAddress getEdr(String transferProcessId) {
        var dataAddressRaw = baseManagementRequest()
                .contentType(ContentType.JSON)
                .when()
                .get("/v3/edrs/{id}/dataaddress", transferProcessId)
                .then()
                .log().ifError()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract().body().as(new TypeRef<Map<String, Object>>() {
                });


        var builder = DataAddress.Builder.newInstance();
        dataAddressRaw.forEach(builder::property);
        return builder.build();

    }

    /**
     * Pull data from provider using EDR.
     *
     * @param edr           endpoint data reference
     * @param queryParams   query parameters
     * @param bodyAssertion assertion to be verified on the body
     */
    public void pullData(DataAddress edr, Map<String, String> queryParams, ThrowingConsumer<String> bodyAssertion) {
        var data = given()
                .baseUri(edr.getStringProperty("endpoint"))
                .header("Authorization", edr.getStringProperty("authorization"))
                .queryParams(queryParams)
                .when()
                .get()
                .then()
                .log().ifError()
                .statusCode(200)
                .extract().body().asString();

        Assertions.assertThat(data).satisfies(bodyAssertion);
    }

    public static class Builder extends Participant.Builder<ControlPlaneApi, ControlPlaneApi.Builder> {

        protected Builder() {
            super(new ControlPlaneApi());
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder managementUrl(LazySupplier<URI> managementUrl) {
            participant.controlPlaneManagement = managementUrl;
            return this;
        }

        public Builder protocolUrl(LazySupplier<URI> managementUrl) {
            participant.controlPlaneProtocol = managementUrl;
            return this;
        }

        @Override
        public ControlPlaneApi build() {
            super.build();
            var headers = Map.of("x-api-key", API_KEY);
            participant.enrichManagementRequest = req -> req.headers(headers);
            return participant;
        }
    }
}
