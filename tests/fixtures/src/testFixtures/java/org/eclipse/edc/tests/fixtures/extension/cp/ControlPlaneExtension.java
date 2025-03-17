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

package org.eclipse.edc.tests.fixtures.extension.cp;

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

import static org.eclipse.edc.boot.BootServicesExtension.PARTICIPANT_ID;
import static org.eclipse.edc.util.io.Ports.getFreePort;

/**
 * {@link ComponentExtension} for the Control Plane. Provides a default configuration for the Control Plane
 * and can inject {@link ControlPlaneApi} and {@link ControlPlaneRuntime} instances in test methods.
 */
public class ControlPlaneExtension extends ComponentExtension {

    public static final String API_KEY = "password";

    protected final LazySupplier<Endpoint> controlPlaneDefault = new LazySupplier<>(() -> new Endpoint(URI.create("http://localhost:" + getFreePort()), Map.of()));
    protected final LazySupplier<Endpoint> controlPlaneControl = new LazySupplier<>(() -> new Endpoint(URI.create("http://localhost:" + getFreePort() + "/control"), Map.of()));
    protected final URI controlPlaneVersion = URI.create("http://localhost:" + getFreePort() + "/version");
    private final int httpProvisionerPort = getFreePort();

    private final LazySupplier<ControlPlaneApi> controlPlaneApi;
    private final ControlPlaneRuntime controlPlaneRuntime;

    private boolean shouldInjectControlPlaneApi = true;

    private ControlPlaneExtension(EmbeddedRuntime runtime, boolean shouldInjectControlPlaneApi) {
        super(runtime);
        this.shouldInjectControlPlaneApi = shouldInjectControlPlaneApi;
        controlPlaneApi = new LazySupplier<>(() -> ControlPlaneApi.Builder.newInstance()
                .id(getId())
                .name(getName())
                .build());

        controlPlaneRuntime = new ControlPlaneRuntime(this);
    }

    public Endpoint getControlPlaneControl() {
        return controlPlaneControl.get();
    }

    public ControlPlaneApi getControlPlaneApi() {
        return controlPlaneApi.get();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        if (isParameterSupported(parameterContext, ControlPlaneApi.class) && shouldInjectControlPlaneApi) {
            return true;
        } else if (isParameterSupported(parameterContext, ControlPlaneRuntime.class)) {
            return true;
        }

        return super.supportsParameter(parameterContext, extensionContext);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        if (isParameterSupported(parameterContext, ControlPlaneApi.class) && shouldInjectControlPlaneApi) {
            return controlPlaneApi.get();
        } else if (isParameterSupported(parameterContext, ControlPlaneRuntime.class)) {
            return controlPlaneRuntime;
        }
        return super.resolveParameter(parameterContext, extensionContext);
    }

    @Override
    public Config getConfiguration() {
        return ConfigFactory.fromMap(new HashMap<>() {
            {
                put(PARTICIPANT_ID, id);
                put("web.http.port", String.valueOf(controlPlaneDefault.get().getUrl().getPort()));
                put("web.http.path", "/api");
                put("web.http.protocol.port", String.valueOf(controlPlaneApi.get().getControlPlaneProtocol().getPort()));
                put("web.http.protocol.path", controlPlaneApi.get().getControlPlaneProtocol().getPath());
                put("web.http.management.port", String.valueOf(controlPlaneApi.get().getControlPlaneManagement().getPort()));
                put("web.http.management.path", controlPlaneApi.get().getControlPlaneManagement().getPath());
                put("web.http.management.auth.type", "tokenbased");
                put("web.http.management.auth.key", API_KEY);
                put("web.http.version.port", String.valueOf(controlPlaneVersion.getPort()));
                put("web.http.version.path", controlPlaneVersion.getPath());
                put("web.http.control.port", String.valueOf(controlPlaneControl.get().getUrl().getPort()));
                put("web.http.control.path", controlPlaneControl.get().getUrl().getPath());
                put("edc.dsp.callback.address", controlPlaneApi.get().getControlPlaneProtocol().toString());
                put("edc.transfer.send.retry.limit", "1");
                put("edc.transfer.send.retry.base-delay.ms", "100");
                put("edc.negotiation.consumer.send.retry.limit", "1");
                put("edc.negotiation.provider.send.retry.limit", "1");
                put("edc.negotiation.consumer.send.retry.base-delay.ms", "100");
                put("edc.negotiation.provider.send.retry.base-delay.ms", "100");

                put("edc.negotiation.consumer.state-machine.iteration-wait-millis", "50");
                put("edc.negotiation.provider.state-machine.iteration-wait-millis", "50");
                put("edc.transfer.state-machine.iteration-wait-millis", "50");

                put("provisioner.http.entries.default.provisioner.type", "provider");
                put("provisioner.http.entries.default.endpoint", "http://localhost:%d/provision".formatted(httpProvisionerPort));
                put("provisioner.http.entries.default.data.address.type", "HttpProvision");
                put("edc.dsp.well-known-path.enabled", "true");
            }
        });
    }


    public static class Builder extends ComponentExtension.Builder<ControlPlaneExtension, Builder> {
        private boolean shouldInjectControlPlaneApi = true;

        protected Builder() {
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder shouldInjectControlPlaneApi(boolean shouldInjectControlPlaneApi) {
            this.shouldInjectControlPlaneApi = shouldInjectControlPlaneApi;
            return this;
        }

        protected ControlPlaneExtension internalBuild() {
            return new ControlPlaneExtension(this.runtime, shouldInjectControlPlaneApi);
        }
    }
}
