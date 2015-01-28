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
import org.opendaylight.groupbasedpolicy.renderer.ofoverlay.EndpointListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.groupbasedpolicy.common.rev140421.L2FloodDomainId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.groupbasedpolicy.endpoint.rev140421.endpoints.Endpoint;
import org.opendaylight.yang.gen.v1.urn.opendaylight.groupbasedpolicy.policy.rev140421.Tenants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.groupbasedpolicy.policy.rev140421.tenants.Tenant;
import org.opendaylight.yang.gen.v1.urn.opendaylight.groupbasedpolicy.policy.rev140421.tenants.tenant.L2BridgeDomain;
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

public class L2DomainManager implements AutoCloseable, DataChangeListener {
	private static final Logger LOG = LoggerFactory
			.getLogger(L2DomainManager.class);
	static ApiConnector apiConnector;
	
	private static final InstanceIdentifier<L2FloodDomain> floodIid = InstanceIdentifier
			.builder(Tenants.class).child(Tenant.class)
			.child(L2FloodDomain.class).build();

	private ListenerRegistration<DataChangeListener> listenerReg;

	private final DataBroker dataProvider;
	private List<L2DomainListener> listeners = new CopyOnWriteArrayList<>();
	
	public L2DomainManager(DataBroker dataProvider,
			RpcProviderRegistry rpcRegistry, ScheduledExecutorService executor) {

		super();
		this.dataProvider = dataProvider;
		if (dataProvider != null) {
			
			listenerReg = dataProvider.registerDataChangeListener(
					LogicalDatastoreType.CONFIGURATION, floodIid, this,
					DataChangeScope.ONE);

		} else
			listenerReg = null;

		LOG.debug("Initialized OC L2 Domain manager");
	}

	 
    public void registerListener(L2DomainListener listener) {
        listeners.add(listener);
    }
    
	// ******************
	// DataChangeListener
	// ******************

	@Override
	public void onDataChanged(
			AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change) {
		String tenantid = "4d76bece-8f58-4928-9f76-14dcbd465df6";
		for (DataObject dao : change.getCreatedData().values()) {
			if (dao instanceof L2FloodDomain)
			try {
				createL2FloodDomain((L2FloodDomain)dao, tenantid);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		Map<InstanceIdentifier<?>,DataObject> d = change.getUpdatedData();
        for (Entry<InstanceIdentifier<?>, DataObject> entry : d.entrySet()) {
            if (!(entry.getValue() instanceof L2FloodDomain)) continue;
            DataObject old = change.getOriginalData().get(entry.getKey());
            L2FloodDomain olddata = null;
            if (old != null && old instanceof L2FloodDomain)
                olddata = (L2FloodDomain)old;
//            updateL2FloodDomain(olddata, (L2FloodDomain)entry.getValue());
        }
	}

	@Override
	public void close() throws Exception {
		// TODO Auto-generated method stub

	}
	
	public void createL2FloodDomain(L2FloodDomain l2FloodDomain, String tenantid) throws IOException{
		int output= canCreateFloodDomain(l2FloodDomain, tenantid);
		createFloodDomain(l2FloodDomain, tenantid);
	}
	
	
	public int canCreateFloodDomain(L2FloodDomain l2FloodDomain, String tenantID) {
        if (l2FloodDomain == null) {
            LOG.error("l2FloodDomain object can't be null..");
            return HttpURLConnection.HTTP_BAD_REQUEST;
        }
        apiConnector = OcRenderer.apiConnector;
        
        if (l2FloodDomain.getId() == null || l2FloodDomain.getName() == null 
        		|| l2FloodDomain.getId().equals("") || l2FloodDomain.getName().equals("")) {
            LOG.error("l2FloodDomain id or name can't be null/empty...");
            return HttpURLConnection.HTTP_BAD_REQUEST;
        }
        if (tenantID == null) {
            LOG.error("Network tenant Id can not be null");
            return HttpURLConnection.HTTP_BAD_REQUEST;
        }
        try {
            String networkUUID = l2FloodDomain.getId().toString();
            if (networkUUID != null){
            	String[] uuid = networkUUID.split("=");
            	networkUUID = uuid[1].replace("]", "");
            }
            String projectUUID = tenantID;
            try {
                if (!(networkUUID.contains("-"))) {
                	networkUUID = Utils.uuidFormater(networkUUID);
                }
                if (!(projectUUID.contains("-"))) {
                    projectUUID = Utils.uuidFormater(projectUUID);
                }
                boolean isValidNetworkUUID = Utils.isValidHexNumber(networkUUID);
                boolean isValidprojectUUID = Utils.isValidHexNumber(projectUUID);
                if (!isValidNetworkUUID || !isValidprojectUUID) {
                    LOG.info("Badly formed Hexadecimal UUID...");
                    return HttpURLConnection.HTTP_BAD_REQUEST;
                }
                projectUUID = UUID.fromString(projectUUID).toString();
                networkUUID = UUID.fromString(networkUUID).toString();
            } catch (Exception ex) {
                LOG.error("UUID input incorrect", ex);
                return HttpURLConnection.HTTP_BAD_REQUEST;
            }
            
            Project project = (Project) apiConnector.findById(Project.class, projectUUID);
            if (project == null) {
                try {
                    Thread.currentThread();
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    LOG.error("InterruptedException :    ", e);
                    return HttpURLConnection.HTTP_BAD_REQUEST;
                }
               project = (Project) apiConnector.findById(Project.class, projectUUID);
                if (project == null) {
                    LOG.error("Could not find projectUUID...");
                    return HttpURLConnection.HTTP_NOT_FOUND;
                }
           }
           VirtualNetwork virtualNetworkById = (VirtualNetwork) apiConnector.findById(VirtualNetwork.class, networkUUID);
            if (virtualNetworkById != null) {
                LOG.warn("Network already exists with UUID" + networkUUID);
                return HttpURLConnection.HTTP_FORBIDDEN;
            }
            return HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            LOG.error("Exception :   " + e);
            return HttpURLConnection.HTTP_INTERNAL_ERROR;
        }
    }

    /**
     * Invoked to create the specified Neutron Network.
     *
     * @param network
     *            An instance of new Neutron Network object.
     */
    private void createFloodDomain(L2FloodDomain l2FloodDomain, String tenantid) throws IOException {
        VirtualNetwork virtualNetwork = new VirtualNetwork();
        virtualNetwork = mapNetworkProperties(l2FloodDomain, virtualNetwork, tenantid);
        boolean networkCreated;
        try {
            networkCreated = apiConnector.create(virtualNetwork);
            LOG.debug("networkCreated:   " + networkCreated);
            if (!networkCreated) {
                LOG.warn("Network creation failed..");
            }
        } catch (IOException ioEx) {
            LOG.error("Exception : " + ioEx);
        }
        LOG.info("Network : " + virtualNetwork.getName() + "  having UUID : " + virtualNetwork.getUuid() + "  sucessfully created...");
    }
    
    /**
     * Invoked to map the NeutronNetwork object properties to the virtualNetwork
     * object.
     *
     * @param neutronNetwork
     *            An instance of new Neutron Network object.
     * @param virtualNetwork
     *            An instance of new virtualNetwork object.
     * @return {@link VirtualNetwork}
     */
    private VirtualNetwork mapNetworkProperties(L2FloodDomain l2FloodDomain, VirtualNetwork virtualNetwork, String tenantid) {
        String networkUUID = l2FloodDomain.getId().toString();
        if (networkUUID != null){
        	String[] uuid = networkUUID.split("=");
        	networkUUID = uuid[1].replace("]", "");
        }
        String projectUUID = tenantid;
        String networkName = l2FloodDomain.getName().toString();
        if (networkName != null){
        	String[] name = networkName.split("=");
        	networkName = name[1].replace("]", "");
        }
        try {
            if (!(networkUUID.contains("-"))) {
                networkUUID = Utils.uuidFormater(networkUUID);
            }
            networkUUID = UUID.fromString(networkUUID).toString();
            if (!(projectUUID.contains("-"))) {
                projectUUID = Utils.uuidFormater(projectUUID);
            }
            projectUUID = UUID.fromString(projectUUID).toString();
            Project project = (Project) apiConnector.findById(Project.class, projectUUID);
            virtualNetwork.setParent(project);
        } catch (Exception ex) {
            LOG.error("UUID input incorrect", ex);
        }
        virtualNetwork.setName(networkName);
        virtualNetwork.setUuid(networkUUID);
        virtualNetwork.setDisplayName(networkName);
        return virtualNetwork;
    }
}