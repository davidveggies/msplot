package org.cytoscape.msplot;

import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.events.SelectedNodesAndEdgesEvent;
import org.cytoscape.model.events.SelectedNodesAndEdgesListener;
import java.util.Collection;
import java.util.Arrays;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class EdgeListener implements SelectedNodesAndEdgesListener {
    private final MSPlotWindow plotWindow;
    private static final Logger logger = Logger.getLogger(EdgeListener.class.getName());
    
    static {
        logger.info("Attempting to set up file handler for logging.");
        try {
            // Create a FileHandler that writes log messages to a file
            FileHandler fileHandler = new FileHandler("F:\\edge_selection_listener.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.info("File handler set up successfully.");
        } catch (Exception e) {
            logger.severe("Failed to set up file handler for logger: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public EdgeListener(MSPlotWindow plotWindow) {
        this.plotWindow = plotWindow;
    }
    
    @Override
    public void handleEvent(SelectedNodesAndEdgesEvent event) {
        logger.info("Edge selection event triggered");
        logger.info("Event source: " + event.getSource().getClass().getName());
        Collection<CyEdge> selectedEdges = event.getSelectedEdges();
        
        if (selectedEdges.isEmpty()) {
            logger.info("No edges selected");
            return;
        }
        
        try {
            // Get the first selected edge
            CyEdge selectedEdge = selectedEdges.iterator().next();
            
            if (selectedEdge == null) {
                logger.warning("Edge object is null for a selected edge.");
                return;
            }
            
            logger.info("Selected edge found: " + selectedEdge.getSUID());
            
            // Retrieve nodes connected by the edge
            CyNode sourceNode = selectedEdge.getSource();
            CyNode targetNode = selectedEdge.getTarget();
            
            if (sourceNode == null || targetNode == null) {
                logger.warning("Source or target node is null for edge: " + selectedEdge.getSUID());
                return;
            }
            
            logger.info(String.format("Edge %d connects nodes: source=%d, target=%d",
                selectedEdge.getSUID(), sourceNode.getSUID(), targetNode.getSUID()));
            
            // Get the network from the event
            CyNetwork network = event.getSource();
            
            // Retrieve MS2 data for the nodes
            CyTable nodeTable = network.getDefaultNodeTable();
            
            String mzValuesStr1 = nodeTable.getRow(sourceNode.getSUID()).get("ms2mzvalues", String.class);
            String intensitiesStr1 = nodeTable.getRow(sourceNode.getSUID()).get("ms2intensities", String.class);
            String mzValuesStr2 = nodeTable.getRow(targetNode.getSUID()).get("ms2mzvalues", String.class);
            String intensitiesStr2 = nodeTable.getRow(targetNode.getSUID()).get("ms2intensities", String.class);
            
            String nodeName1 = nodeTable.getRow(sourceNode.getSUID()).get("name", String.class);
            String nodeName2 = nodeTable.getRow(targetNode.getSUID()).get("name", String.class);
            
            double[] mzValues1 = mzValuesStr1 != null ? Arrays.stream(mzValuesStr1.split(",")).map(String::trim).mapToDouble(Double::parseDouble).toArray() : new double[0];
            double[] intensities1 = intensitiesStr1 != null ? Arrays.stream(intensitiesStr1.split(",")).map(String::trim).mapToDouble(Double::parseDouble).toArray() : new double[0];
            double[] mzValues2 = mzValuesStr2 != null ? Arrays.stream(mzValuesStr2.split(",")).map(String::trim).mapToDouble(Double::parseDouble).toArray() : new double[0];
            double[] intensities2 = intensitiesStr2 != null ? Arrays.stream(intensitiesStr2.split(",")).map(String::trim).mapToDouble(Double::parseDouble).toArray() : new double[0];
            
            // Update the plot with mirror data
            plotWindow.updateMirrorPlot(mzValues1, intensities1, nodeName1, mzValues2, intensities2, nodeName2);
            
        } catch (Exception ex) {
            logger.severe("Error processing edge selection: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
} 