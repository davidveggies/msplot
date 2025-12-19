package org.cytoscape.msplot;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.events.RowsSetEvent;
import org.cytoscape.model.events.RowsSetListener;
import java.util.logging.*;
import java.io.IOException;

public class NodeSelectionListener implements RowsSetListener {
    private final MSPlotWindow plotWindow;
    private static final Logger logger = Logger.getLogger(NodeSelectionListener.class.getName());
    
    static {
        try {
            FileHandler fileHandler = new FileHandler("msplot.log", true);  // Append mode
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.setUseParentHandlers(false);  // Optional: avoid printing to console
            logger.setLevel(Level.ALL); // <-- Make sure logging is ON by default
        } catch (IOException e) {
            System.err.println("Failed to set up logger: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void setLoggingEnabled(boolean enabled) {
        if (enabled) {
            logger.setLevel(Level.ALL);
        } else {
            logger.setLevel(Level.OFF);
        }
    }
    
    public NodeSelectionListener(MSPlotWindow plotWindow) {
        this.plotWindow = plotWindow;
    }
    
    private static String getCellAsString(CyRow row, String columnName) {
        Object value = row.getRaw(columnName);
        if (value == null) {
            return null;
        }
        return value.toString();
    }
    
    @Override
    public void handleEvent(RowsSetEvent e) {
        if (!e.containsColumn("selected")) {
            return;
        }
        
        try {
            CyTable nodeTable = e.getSource();
            
            // Get all selected rows
            List<CyRow> selectedRows = nodeTable.getAllRows().stream()
                .filter(row -> row.get("selected", Boolean.class))
                .collect(Collectors.toList());
            
            int selectedCount = selectedRows.size();
            logger.info("Number of selected nodes: " + selectedCount);
            
            // If exactly 2 nodes selected, show mirror plot
            if (selectedCount == 2) {
                CyRow row1 = selectedRows.get(0);
                CyRow row2 = selectedRows.get(1);
                
                // Get node names
                String nodeName1 = getCellAsString(row1, "name");
                String nodeName2 = getCellAsString(row2, "name");
                
                // Get MS2 data for both nodes
                String mzValuesStr1 = row1.get("ms2mzvalues", String.class);
                String intensitiesStr1 = row1.get("ms2intensities", String.class);
                String mzValuesStr2 = row2.get("ms2mzvalues", String.class);
                String intensitiesStr2 = row2.get("ms2intensities", String.class);
                
                if (mzValuesStr1 != null && intensitiesStr1 != null && 
                    mzValuesStr2 != null && intensitiesStr2 != null) {
                    
                    double[] mzValues1 = Arrays.stream(mzValuesStr1.split(","))
                        .map(String::trim)
                        .mapToDouble(Double::parseDouble)
                        .toArray();
                    double[] intensities1 = Arrays.stream(intensitiesStr1.split(","))
                        .map(String::trim)
                        .mapToDouble(Double::parseDouble)
                        .toArray();
                    double[] mzValues2 = Arrays.stream(mzValuesStr2.split(","))
                        .map(String::trim)
                        .mapToDouble(Double::parseDouble)
                        .toArray();
                    double[] intensities2 = Arrays.stream(intensitiesStr2.split(","))
                        .map(String::trim)
                        .mapToDouble(Double::parseDouble)
                        .toArray();
                    
                    logger.info("Showing mirror plot for nodes: " + nodeName1 + " and " + nodeName2);
                    plotWindow.updateMirrorPlot(mzValues1, intensities1, nodeName1, 
                                                mzValues2, intensities2, nodeName2);
                } else {
                    logger.warning("Missing MS2 data for one or both selected nodes.");
                }
            } 
            // If exactly 1 node selected, show regular plot
            else if (selectedCount == 1) {
                CyRow selectedRow = selectedRows.get(0);
                
                // Get the mz values and intensities from the node attributes
                String mzValuesStr = selectedRow.get("ms2mzvalues", String.class);
                String intensitiesStr = selectedRow.get("ms2intensities", String.class);
                String name = getCellAsString(selectedRow, "name");
                String mz = getCellAsString(selectedRow, "mz");
                String rt = getCellAsString(selectedRow, "rt");
                String rtMean = getCellAsString(selectedRow, "RTMean");
                String formula = getCellAsString(selectedRow, "molecularFormula");

                StringBuilder featureLabelBuilder = new StringBuilder();
                if (name != null && !name.isEmpty()) {
                    featureLabelBuilder.append(name);
                }

                if (mz != null && !mz.isEmpty()) {
                    featureLabelBuilder.append(" | m/z: ").append(mz);
                }

                if ((rt != null && !rt.isEmpty()) || (rtMean != null && !rtMean.isEmpty())) {
                    featureLabelBuilder.append(" | RT: ").append(rt != null ? rt : rtMean);
                }

                if (formula != null && !formula.isEmpty()) {
                    featureLabelBuilder.append(" | Formula: ").append(formula);
                }

                String featureLabel = featureLabelBuilder.toString();
                logger.info("built up featureLabel: "+ featureLabel);
                		
                if (mzValuesStr != null && intensitiesStr != null) {
                    // Parse the values (assuming comma-separated values)
                    double[] mzValues = Arrays.stream(mzValuesStr.split(","))
                        .map(String::trim)
                        .mapToDouble(Double::parseDouble)
                        .toArray();
                        
                    double[] intensities = Arrays.stream(intensitiesStr.split(","))
                        .map(String::trim)
                        .mapToDouble(Double::parseDouble)
                        .toArray();
                    
                    // Update the plot
                    plotWindow.updatePlot(mzValues, intensities, featureLabel);
                } else {
                    logger.warning("Node attributes 'mzvalues' or 'intensities' are null.");
                }
            }
            // If 0 or more than 2 nodes selected, do nothing (or could clear the plot)
        } catch (Exception ex) {
            logger.severe("Error processing node selection: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
