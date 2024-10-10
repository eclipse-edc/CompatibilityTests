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

plugins {
    id("application")
    alias(libs.plugins.shadow)
    alias(libs.plugins.docker)
}

dependencies {
    runtimeOnly(stableLibs.edc.api.observability)
    runtimeOnly(stableLibs.bundles.dataplane)
    runtimeOnly(stableLibs.edc.jsonld) // needed by the DataPlaneSignalingApi
    runtimeOnly(stableLibs.edc.dpf.selector.client) // for the selector service -> self registration

    // uncomment the following lines to compile with Hashicorp Vault and Postgres persistence
    // runtimeOnly(stableLibs.edc.vault.hashicorp)
    runtimeOnly(stableLibs.bundles.sql.dataplane)

}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    exclude("**/pom.properties", "**/pom.xml")
    mergeServiceFiles()
    archiveFileName.set("${project.name}.jar")
}

application {
    mainClass.set("org.eclipse.edc.boot.system.runtime.BaseRuntime")
}

edcBuild {
    publish.set(false)
}
