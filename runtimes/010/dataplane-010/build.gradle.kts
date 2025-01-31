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

import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin

plugins {
    id("application")
    alias(libs.plugins.shadow)
    alias(libs.plugins.docker)
}

dependencies {
    implementation(libs010.edc.runtime.metamodel)
    implementation(libs010.edc.boot.spi)
    runtimeOnly(libs010.edc.spi.core)
    runtimeOnly(libs010.edc.api.observability)
    runtimeOnly(libs010.edc.core.connector)
    runtimeOnly(libs010.edc.boot)
    runtimeOnly(libs010.bundles.dataplane)
    runtimeOnly(libs010.edc.jsonld) // needed by the DataPlaneSignalingApi
    runtimeOnly(libs010.edc.dpf.selector.client) // for the selector service -> self registration

    // uncomment the following lines to compile with Hashicorp Vault and Postgres persistence
    // runtimeOnly(libs.edc.vault.hashicorp)
    runtimeOnly(libs010.bundles.sql.dataplane)

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

// configure the "dockerize" task
tasks.register("dockerize", DockerBuildImage::class) {
    val dockerContextDir = project.projectDir
    dockerFile.set(file("$dockerContextDir/src/main/docker/Dockerfile"))
    images.add("${project.name}:${libs010.versions.edc.get()}")
    images.add("${project.name}:latest")
    // specify platform with the -Dplatform flag:
    if (System.getProperty("platform") != null)
        platform.set(System.getProperty("platform"))
    buildArgs.put("JAR", "build/libs/${project.name}.jar")
    inputDir.set(file(dockerContextDir))
    dependsOn(tasks.named(ShadowJavaPlugin.SHADOW_JAR_TASK_NAME))
}