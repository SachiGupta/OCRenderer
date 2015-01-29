/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.groupbasedpolicy.renderer.oc;

import org.opendaylight.yang.gen.v1.urn.opendaylight.groupbasedpolicy.common.rev140421.L2FloodDomainId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.groupbasedpolicy.policy.rev140421.tenants.tenant.L2FloodDomainKey;


/**
 * A listener to events related to L2 flood domain being added, removed or updated.
 */
public interface L2DomainListener {
    /**
     * The L2 Flood Domain has been added or updated
     * @param L2FloodDomainId L2 Flood domain id
     */
    public void L2DomainUpdated(L2FloodDomainId l2domainid);
}
