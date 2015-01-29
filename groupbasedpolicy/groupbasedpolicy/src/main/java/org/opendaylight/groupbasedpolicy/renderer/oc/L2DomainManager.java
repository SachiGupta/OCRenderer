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
import org.opendaylight.yang.gen.v1.urn.opendaylight.groupbasedpolicy.endpoint.rev140421.unregister.endpoint.input.L2;
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

	private static final InstanceIdentifier<Tenant> TenantIid = InstanceIdentifier
			.builder(Tenants.class).child(Tenant.class).build();
	
	private ListenerRegistration<DataChangeListener> listenerReg;

	private final DataBroker dataProvider;
	private List<L2DomainListener> listeners = new CopyOnWriteArrayList<>();
	
	public L2DomainManager(DataBroker dataProvider,
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

	 
    public void registerListener(L2DomainListener listener) {
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
				createL2FloodDomain((Tenant)dao);
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
                	updateL2FloodDomain(olddata, (Tenant)entry.getValue());
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
	
	public void createL2FloodDomain(Tenant tenant) throws IOException{
		String tenantID = tenant.getId().toString();
		if (tenant.getL2FloodDomain() != null){
			for(L2FloodDomain l2FloodDomain : tenant.getL2FloodDomain()) {
				int output= canCreateFloodDomain(l2FloodDomain, tenantID);
				createFloodDomain(l2FloodDomain, tenantID);
			}
		}
	}
	
	
	public int canCreateFloodDomain(L2FloodDomain l2FloodDomain, String tenantID) {
        apiConnector = OcRenderer.apiConnector;
        
        if (l2FloodDomain.getId() == null || l2FloodDomain.getName() == null 
        		|| l2FloodDomain.getId().equals("") || l2FloodDomain.getName().equals("")) {
            LOG.error("l2FloodDomain id or name can't be null/empty...");
            return HttpURLConnection.HTTP_BAD_REQUEST;
        }
        
        try {
            String networkUUID = Utils.uuidNameFormat(l2FloodDomain.getId().toString());
            String projectUUID = Utils.uuidNameFormat(tenantID);
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
        String networkUUID = Utils.uuidNameFormat(l2FloodDomain.getId().toString());
        String projectUUID = Utils.uuidNameFormat(tenantid);
        String networkName = Utils.uuidNameFormat(l2FloodDomain.getName().toString());
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
    
    public void updateL2FloodDomain(Tenant oldData, Tenant newData) throws IOException{
		String tenantID = newData.getId().toString();
		if (newData.getL2FloodDomain() != null){
			if(oldData.getL2FloodDomain() == null){
				for(L2FloodDomain l2FloodDomain : newData.getL2FloodDomain()) {
					int output= canCreateFloodDomain(l2FloodDomain, tenantID);
					createFloodDomain(l2FloodDomain, tenantID);
				}
			}
			else {
				for(L2FloodDomain l2FloodDomainNew : newData.getL2FloodDomain()) {
					for(L2FloodDomain l2FloodDomainOld : oldData.getL2FloodDomain()) {
						String l2FloodDomainNewId = Utils.uuidNameFormat(l2FloodDomainNew.getId().toString());
						String l2FloodDomainOldId = Utils.uuidNameFormat(l2FloodDomainOld.getId().toString());
						if(l2FloodDomainNewId.equals(l2FloodDomainOldId)){
							int output= canUpdateFloodDomain(l2FloodDomainNew, l2FloodDomainOld, tenantID);
							updateFloodDomain(l2FloodDomainNew, tenantID);
						}
						else{
							int output= canCreateFloodDomain(l2FloodDomainNew, tenantID);
							createFloodDomain(l2FloodDomainNew, tenantID);
						}
					}
				}
			}
		}
	}
    
    public int canUpdateFloodDomain(L2FloodDomain l2FloodDomainNew, L2FloodDomain l2FloodDomainOld, String tenantID ) {
        VirtualNetwork virtualnetwork;
        apiConnector = OcRenderer.apiConnector;
        
        String networkUUID = Utils.uuidNameFormat(l2FloodDomainOld.getId().toString());
        String projectUUID = Utils.uuidNameFormat(tenantID);
        try {
            if (!(networkUUID.contains("-"))) {
                networkUUID = Utils.uuidFormater(networkUUID);
                networkUUID = UUID.fromString(networkUUID).toString();
            }
            if (!(projectUUID.contains("-"))) {
                projectUUID = Utils.uuidFormater(projectUUID);
                projectUUID = UUID.fromString(projectUUID).toString();
            }
        } catch (Exception ex) {
            LOG.error("UUID input incorrect", ex);
        }
        if (("").equals(l2FloodDomainNew.getName())) {
            LOG.error("L2 flood domain Name to be update can't be empty..");
            return HttpURLConnection.HTTP_BAD_REQUEST;
        }
        try {
            Project project = (Project) apiConnector.findById(Project.class, projectUUID);
            String virtualNetworkByName = apiConnector.findByName(VirtualNetwork.class, project, l2FloodDomainNew.getName().toString());
            if (virtualNetworkByName != null) {
                LOG.warn("L2 flood domain with name  " + l2FloodDomainNew.getName() + "  already exists with UUID : " + virtualNetworkByName);
                return HttpURLConnection.HTTP_FORBIDDEN;
            }
        } catch (IOException ioEx) {
            LOG.error("IOException :     " + ioEx);
            return HttpURLConnection.HTTP_INTERNAL_ERROR;
        }
        try {
            virtualnetwork = (VirtualNetwork) apiConnector.findById(VirtualNetwork.class, networkUUID);
        } catch (IOException ex) {
            LOG.error("Exception :     " + ex);
            return HttpURLConnection.HTTP_INTERNAL_ERROR;
        }
        if (virtualnetwork == null) {
            LOG.error("No L2 flood domain exists for the specified UUID...");
            return HttpURLConnection.HTTP_FORBIDDEN;
        }
        return HttpURLConnection.HTTP_OK;
    }
    
    /**
     * Invoked to update the L2 Flood Domain
     *
     */
    private void updateFloodDomain(L2FloodDomain l2FloodDomain, String tenantid) throws IOException {
        String networkUUID = Utils.uuidNameFormat(l2FloodDomain.getId().toString());
        try {
            if (!(networkUUID.contains("-"))) {
                networkUUID = Utils.uuidFormater(networkUUID);
            }
            networkUUID = UUID.fromString(networkUUID).toString();
        } catch (Exception ex) {
            LOG.error("UUID input incorrect", ex);
        }
        VirtualNetwork virtualNetwork = (VirtualNetwork) apiConnector.findById(VirtualNetwork.class, networkUUID);
        virtualNetwork.setDisplayName(Utils.uuidNameFormat(l2FloodDomain.getName().toString()));
        boolean networkUpdate;
        try {
            networkUpdate = apiConnector.update(virtualNetwork);
            if (!networkUpdate) {
                LOG.warn("L2 flood domain Updation failed..");
            }
        } catch (IOException e) {
            LOG.warn("L2 flood domain Updation failed..");
        }
        LOG.info("L2 flood domain having UUID : " + virtualNetwork.getUuid() + "  has been sucessfully updated...");
    }
}