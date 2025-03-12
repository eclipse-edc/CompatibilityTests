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

plugins {
    `java-library`
}

dependencies {
    testImplementation(libs.edc.junit)
    testImplementation(libs.edc.spi.dataplane)
    testImplementation(libs.edc.spi.did)
    testImplementation(libs.edc.spi.transaction.datasource)
    testImplementation(libs.edc.spi.verifiablecredentials)
    testImplementation(libs.edc.ih.spi.participants)
    testImplementation(libs.edc.ih.spi.holder.requests)
    testImplementation(libs.edc.ih.issuer.spi.holders)
    testImplementation(libs.edc.ih.issuer.spi.issuance)
    testImplementation(libs.edc.lib.sql)
    testImplementation(libs.jakarta.json.api)
    testImplementation(libs.jackson.datatype.jakarta.jsonp)
    testImplementation(libs.restAssured)
    testImplementation(libs.awaitility)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.bouncyCastle.bcpkixJdk18on)
    testImplementation(testFixtures(libs.edc.api.management.test.fixtures))
    testImplementation(testFixtures(libs.edc.sql.test.fixtures))
    testImplementation(testFixtures(libs.edc.ih.test.fixtures))
    testImplementation(testFixtures(project(":tests:fixtures")))
}