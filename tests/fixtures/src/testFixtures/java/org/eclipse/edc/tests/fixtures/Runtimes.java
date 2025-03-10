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

package org.eclipse.edc.tests.fixtures;

public class Runtimes {


    public interface Issuer {
        String ID = "issuer";
        String[] MODULES = new String[]{
                ":runtimes:snapshot:issuer-snapshot"
        };
    }

    public interface IdentityHub {
        String[] MODULES = new String[]{
                ":runtimes:snapshot:identity-hub-snapshot"
        };
    }

    public interface DataPlane {
        String[] MODULES = new String[]{
                ":runtimes:snapshot:dataplane-snapshot"
        };
    }

    public interface ControlPlane {
        String[] MODULES = new String[]{
                ":runtimes:snapshot:controlplane-snapshot"
        };

        String[] DCP_MODULES = new String[]{
                ":runtimes:snapshot:controlplane-snapshot-dcp"
        };

    }
    
}
