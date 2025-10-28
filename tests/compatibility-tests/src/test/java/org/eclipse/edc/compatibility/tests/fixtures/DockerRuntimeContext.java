/*
 *  Copyright (c) 2025 Metaform Systems, Inc.
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Metaform Systems, Inc. - initial API and implementation
 *
 */

package org.eclipse.edc.compatibility.tests.fixtures;

import org.eclipse.edc.junit.utils.Endpoints;
import org.eclipse.edc.junit.utils.LazySupplier;

import java.net.URI;
import java.util.Map;

public class DockerRuntimeContext {

    private final Endpoints endpoints;

    private final Map<String, String> config;

    public DockerRuntimeContext(Endpoints endpoints, Map<String, String> config) {
        this.endpoints = endpoints;
        this.config = config;
    }

    public LazySupplier<URI> getEndpoint(String name) {
        return endpoints.getEndpoint(name);
    }

    public Map<String, String> getConfig() {
        return config;
    }
}
