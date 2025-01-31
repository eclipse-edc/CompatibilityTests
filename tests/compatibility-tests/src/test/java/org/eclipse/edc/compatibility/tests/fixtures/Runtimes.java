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

package org.eclipse.edc.compatibility.tests.fixtures;

import org.eclipse.edc.junit.extensions.ClasspathReader;
import org.eclipse.edc.junit.extensions.EmbeddedRuntime;

import java.net.URL;
import java.util.Map;

public enum Runtimes {

    CONTROL_PLANE(":runtimes:snapshot:controlplane-snapshot"),

    DATA_PLANE(":runtimes:snapshot:dataplane-snapshot");

    private final String[] modules;
    private URL[] classpathEntries;

    Runtimes(String... modules) {
        this.modules = modules;
    }

    public EmbeddedRuntime create(String name, Map<String, String> configuration) {
        if (classpathEntries == null) {
            classpathEntries = ClasspathReader.classpathFor(modules);
        }
        return new EmbeddedRuntime(name, configuration, classpathEntries);
    }
}
