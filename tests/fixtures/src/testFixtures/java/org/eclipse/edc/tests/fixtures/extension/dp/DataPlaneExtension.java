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

package org.eclipse.edc.tests.fixtures.extension.dp;

import org.eclipse.edc.identityhub.tests.fixtures.common.ComponentExtension;
import org.eclipse.edc.identityhub.tests.fixtures.common.Endpoint;
import org.eclipse.edc.identityhub.tests.fixtures.common.LazySupplier;
import org.eclipse.edc.junit.extensions.EmbeddedRuntime;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.eclipse.edc.util.io.Ports.getFreePort;

/**
 * {@link ComponentExtension} for the Data Plane. Provides a default configuration for the Data Plane
 * and can inject {@link DataPlaneRuntime} instance in test methods.
 */
public class DataPlaneExtension extends ComponentExtension {

    protected final LazySupplier<Endpoint> dataPlaneDefault = new LazySupplier<>(() -> new Endpoint(URI.create("http://localhost:" + getFreePort()), Map.of()));
    protected final LazySupplier<Endpoint> dataPlaneControl = new LazySupplier<>(() -> new Endpoint(URI.create("http://localhost:" + getFreePort() + "/control"), Map.of()));
    protected final LazySupplier<Endpoint> dataPlanePublic = new LazySupplier<>(() -> new Endpoint(URI.create("http://localhost:" + getFreePort() + "/public"), Map.of()));
    private final DataPlaneRuntime dataPlaneRuntime;

    private DataPlaneExtension(EmbeddedRuntime runtime) {
        super(runtime);
        dataPlaneRuntime = new DataPlaneRuntime(this);
    }


    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        if (isParameterSupported(parameterContext, DataPlaneRuntime.class)) {
            return true;
        }

        return super.supportsParameter(parameterContext, extensionContext);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        if (isParameterSupported(parameterContext, DataPlaneRuntime.class)) {
            return dataPlaneRuntime;
        }
        return super.resolveParameter(parameterContext, extensionContext);
    }

    @Override
    public Config getConfiguration() {
        return ConfigFactory.fromMap(new HashMap<>() {
            {
                put("web.http.port", String.valueOf(dataPlaneDefault.get().getUrl().getPort()));
                put("web.http.path", "/api");
                put("web.http.public.port", String.valueOf(dataPlanePublic.get().getUrl().getPort()));
                put("web.http.public.path", "/public");
                put("web.http.control.port", String.valueOf(dataPlaneControl.get().getUrl().getPort()));
                put("web.http.control.path", dataPlaneControl.get().getUrl().getPath());
                put("edc.dataplane.api.public.baseurl", dataPlanePublic + "/v2/");
                put("edc.transfer.proxy.token.signer.privatekey.alias", "private-key");
                put("edc.transfer.proxy.token.verifier.publickey.alias", "public-key");
                put("edc.dataplane.http.sink.partition.size", "1");
                put("edc.dataplane.state-machine.iteration-wait-millis", "50");
                put("edc.component.id", "dataplane");
            }
        });
    }

    public static class Builder extends ComponentExtension.Builder<DataPlaneExtension, Builder> {
        protected Builder() {
        }

        public static Builder newInstance() {
            return new Builder();
        }

        protected DataPlaneExtension internalBuild() {
            return new DataPlaneExtension(this.runtime);
        }
    }
}
