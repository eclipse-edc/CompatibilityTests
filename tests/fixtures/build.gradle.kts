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
    `java-test-fixtures`
}

dependencies {
    testFixturesImplementation(libs.edc.junit)
    testFixturesImplementation(libs.restAssured)
    testFixturesImplementation(libs.awaitility)
    testFixturesImplementation(libs.edc.spi.dataplane)
    testFixturesImplementation(testFixtures(libs.edc.api.management.test.fixtures))
    testFixturesImplementation(testFixtures(libs.edc.sql.test.fixtures))
    testFixturesImplementation(testFixtures(libs.edc.ih.test.fixtures))
}