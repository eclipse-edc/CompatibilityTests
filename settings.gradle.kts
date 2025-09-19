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

rootProject.name = "CompatibilityTests"

// this is needed to have access to snapshot builds of plugins
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        }
        mavenCentral()
    }
    versionCatalogs {
        create("stableLibs") {
            from(files("./gradle/libs.stable.versions.toml"))
        }
    }
}

// add dependencies
include(":runtimes:snapshot:controlplane-snapshot-base")
include(":runtimes:snapshot:controlplane-snapshot")
include(":runtimes:snapshot:controlplane-snapshot-dcp")
include(":runtimes:snapshot:dataplane-snapshot")
include(":runtimes:snapshot:issuerservice-snapshot")
include(":runtimes:snapshot:identity-hub-snapshot")
include(":runtimes:stable:controlplane-stable")
include(":runtimes:stable:dataplane-stable")
include(":tests:compatibility-tests")
include(":tests:dcp-tests")
include(":tests:fixtures")