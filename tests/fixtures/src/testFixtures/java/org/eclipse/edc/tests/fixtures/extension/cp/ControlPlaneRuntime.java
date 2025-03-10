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

public class ControlPlaneRuntime {

    private final ControlPlaneExtension extension;

    public ControlPlaneRuntime(ControlPlaneExtension extension) {
        this.extension = extension;
    }

    public String getId() {
        return extension.getId();
    }

    public String getName() {
        return extension.getName();
    }

    public <S> S getService(Class<S> klass) {
        return (S) this.extension.getRuntime().getService(klass);
    }

}
