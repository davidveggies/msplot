package org.cytoscape.msplot;

import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.events.RowsSetEvent;
import org.cytoscape.model.events.RowsSetListener;
import org.cytoscape.model.events.SelectedNodesAndEdgesListener;
import org.cytoscape.service.util.AbstractCyActivator;
import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyNetworkFactory;
import org.osgi.framework.BundleContext;
import java.util.Properties;

public class CyActivator extends AbstractCyActivator {
    
    @Override
    public void start(BundleContext bc) throws Exception {
        // Get the Cytoscape Swing Application service
        CySwingApplication cySwingApplication = getService(bc, CySwingApplication.class);
        
        // Get the CyNetworkManager and CyNetworkFactory services
        CyNetworkManager networkManager = getService(bc, CyNetworkManager.class);
        CyNetworkFactory networkFactory = getService(bc, CyNetworkFactory.class);

        // Create or retrieve a network
        CyNetwork network;
        if (networkManager.getNetworkSet().isEmpty()) {
            // Create a new network if none exists
            network = networkFactory.createNetwork();
            networkManager.addNetwork(network);
        } else {
            // Find the largest network (with the most nodes)
            CyNetwork largestNetwork = null;
            int maxNodeCount = -1;
            
            for (CyNetwork currentNetwork : networkManager.getNetworkSet()) {
                int currentNodeCount = currentNetwork.getNodeList().size();
                if (currentNodeCount > maxNodeCount) {
                    maxNodeCount = currentNodeCount;
                    largestNetwork = currentNetwork;
                }
            }
            
            network = largestNetwork;
            System.out.println("Selected largest network with " + maxNodeCount + " nodes");
        }

        // Create the plot window with the network
        MSPlotWindow plotWindow = new MSPlotWindow(cySwingApplication, network);
        
        // Create and register the node selection listener
        NodeSelectionListener nodeListener = new NodeSelectionListener(plotWindow);
        registerService(bc, nodeListener, RowsSetListener.class, new Properties());
        
        // Create and register the edge listener using the new approach
        EdgeListener edgeListener = new EdgeListener(plotWindow);
        registerService(bc, edgeListener, SelectedNodesAndEdgesListener.class, new Properties());
        
        // Show the plot window
        plotWindow.setVisible(true);
    }
}
