/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.groupbasedpolicy.renderer.oc;

import org.opendaylight.yang.gen.v1.urn.opendaylight.groupbasedpolicy.common.rev140421.EndpointGroupId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.groupbasedpolicy.policy.rev140421.tenants.tenant.EndpointGroupKey;


/**
 * A listener to events related to EPG being added, removed or updated.
 */
public interface EndpointGroupListener {
    /**
     * The EPG has been added or updated
     * @param epKey the key for the affected endpoint
     */
    public void endpointGroupUpdated(EndpointGroupId endpointGroupId, EndpointGroupKey egkey);

}
