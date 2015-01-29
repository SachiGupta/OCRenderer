package org.opendaylight.groupbasedpolicy.renderer.oc;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;

import net.juniper.contrail.api.ApiConnector;
import net.juniper.contrail.api.types.Project;
import net.juniper.contrail.api.types.VirtualNetwork;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.groupbasedpolicy.policy.rev140421.Tenants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.groupbasedpolicy.policy.rev140421.tenants.Tenant;
import org.opendaylight.yang.gen.v1.urn.opendaylight.groupbasedpolicy.policy.rev140421.tenants.tenant.EndpointGroup;
import org.opendaylight.yang.gen.v1.urn.opendaylight.groupbasedpolicy.policy.rev140421.tenants.tenant.L2FloodDomain;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

public class EndpointGroupManager implements AutoCloseable, DataChangeListener {
	private static final Logger LOG = LoggerFactory
			.getLogger(EndpointGroupManager.class);
	static ApiConnector apiConnector;

	private static final InstanceIdentifier<Tenant> TenantIid = InstanceIdentifier
			.builder(Tenants.class).child(Tenant.class).build();
	
	private ListenerRegistration<DataChangeListener> listenerReg;

	private final DataBroker dataProvider;
	private List<EndpointGroupListener> listeners = new CopyOnWriteArrayList<>();
	
	public EndpointGroupManager(DataBroker dataProvider,
			RpcProviderRegistry rpcRegistry, ScheduledExecutorService executor) {

		super();
		this.dataProvider = dataProvider;
		if (dataProvider != null) {
			
			listenerReg = dataProvider.registerDataChangeListener(
					LogicalDatastoreType.CONFIGURATION, TenantIid, this,
					DataChangeScope.ONE);

		} else
			listenerReg = null;

		LOG.debug("Initialized OC L2 Domain manager");
	}

	 
    public void registerListener(EndpointGroupListener listener) {
        listeners.add(listener);
    }
    
	// ******************
	// DataChangeListener
	// ******************

	@Override
	public void onDataChanged(
			AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change) {
		for (DataObject dao : change.getCreatedData().values()) {
			if (dao instanceof Tenant)
			try {
				createEndpointGroup((Tenant)dao);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		Map<InstanceIdentifier<?>,DataObject> d = change.getUpdatedData();
        for (Entry<InstanceIdentifier<?>, DataObject> entry : d.entrySet()) {
            if (!(entry.getValue() instanceof Tenant)) continue;
            DataObject old = change.getOriginalData().get(entry.getKey());
            Tenant olddata = null;
            if (old != null && old instanceof Tenant)
                olddata = (Tenant)old;
                try {
                	updateEndpointGroup(olddata, (Tenant)entry.getValue());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
	}

	@Override
	public void close() throws Exception {
		// TODO Auto-generated method stub

	}
	
	/**
     * Invoked when a EndpointGroup create is requested 
     *
     *@param  tenant 
     *                An instance of tenant data
     */
	
	public void createEndpointGroup(Tenant tenant) throws IOException{
		String tenantID = tenant.getId().toString();
		if (tenant.getEndpointGroup() != null){
			for(EndpointGroup endpointGroup : tenant.getEndpointGroup()) {
//				int output= canCreateEndpointGroup(endpointGroup, tenantID);
//				createEndpointGroup(endpointGroup, tenantID);
			}
		}
	}
	
	/**
     * Invoked when a EndpointGroup update is requested 
     *
     *@param  oldData 
     *                An instance of Old tenant data
     *@param  newData 
     *                An instance of New tenant data
     *                
     */
    
    public void updateEndpointGroup(Tenant oldData, Tenant newData) throws IOException{
		String tenantID = newData.getId().toString();
		if (newData.getEndpointGroup() != null){
			if(oldData.getEndpointGroup() == null){
				for(EndpointGroup endpointGroup : newData.getEndpointGroup()) {
//					int output= canCreateEndpointGroup(endpointGroup, tenantID);
//					createEndpointGroup(endpointGroup, tenantID);
				}
			}
			else {
				for(EndpointGroup endpointGroupNew : newData.getEndpointGroup()) {
					for(EndpointGroup endpointGroupOld : oldData.getEndpointGroup()) {
						String endpointGroupNewId = Utils.uuidNameFormat(endpointGroupNew.getId().toString());
						String endpointGroupOldId = Utils.uuidNameFormat(endpointGroupOld.getId().toString());
						if(endpointGroupNewId.equals(endpointGroupOldId)){
//							int output= canUpdateEndpointGroup(endpointGroupNew, endpointGroupOld, tenantID);
//							updateEndpointGroup(endpointGroupNew, tenantID);
						}
						else{
//							int output= canCreateEndpointGroup(endpointGroupNew, tenantID);
//							createEndpointGroup(endpointGroupNew, tenantID);
						}
					}
				}
			}
		}
	}
	
}