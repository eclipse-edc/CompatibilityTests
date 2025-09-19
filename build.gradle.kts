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
    `java-library`
    alias(libs.plugins.edc.build)
}

val javaVersion: String by project
val edcScmUrl: String by project
val edcScmConnection: String by project

buildscript {
    dependencies {
        val version: String by project
        classpath("org.eclipse.edc.autodoc:org.eclipse.edc.autodoc.gradle.plugin:$version")
    }
}

val edcBuildId = libs.plugins.edc.build.get().pluginId

allprojects {
    apply(plugin = edcBuildId)
    apply(plugin = "org.eclipse.edc.autodoc")
    val version: String by project
    // configure which version of the annotation processor to use. defaults to the same version as the plugin
    configure<org.eclipse.edc.plugins.autodoc.AutodocExtension> {
        processorVersion.set(version)
        outputDirectory.set(project.layout.buildDirectory.asFile)
    }

    configure<org.eclipse.edc.plugins.edcbuild.extensions.BuildExtension> {
        pom {
            scmUrl.set(edcScmUrl)
            scmConnection.set(edcScmConnection)
        }
    }

}