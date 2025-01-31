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
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        }
        mavenCentral()
        gradlePluginPortal()
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
        create("libs010") {
            from(files("./gradle/libs.0.10.0.versions.toml"))
        }
    }
}

// add dependencies
include(":runtimes:snapshot:controlplane-snapshot")
include(":runtimes:snapshot:dataplane-snapshot")
include(":runtimes:stable:controlplane-stable")
include(":runtimes:stable:dataplane-stable")
include(":runtimes:010:controlplane-010")
include(":runtimes:010:dataplane-010")
include(":tests:compatibility-tests")