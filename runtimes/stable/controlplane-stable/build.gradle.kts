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
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.shadowJar

plugins {
    id("application")
    alias(libs.plugins.shadow)
    alias(libs.plugins.docker)
}

dependencies {
    runtimeOnly(stableLibs.edc.bom.controlplane)
    runtimeOnly(stableLibs.edc.bom.controlplane.sql)
    runtimeOnly(stableLibs.edc.iam.mock)
}

tasks.shadowJar {
    mergeServiceFiles()
    archiveFileName.set("${project.name}.jar")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
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
    images.add("${project.name}:${stableLibs.versions.edc.get()}")
    images.add("${project.name}:latest")
    // specify platform with the -Dplatform flag:
    if (System.getProperty("platform") != null)
        platform.set(System.getProperty("platform"))
    buildArgs.put("JAR", "build/libs/${project.name}.jar")
    inputDir.set(file(dockerContextDir))
    dependsOn(tasks.shadowJar)
}
