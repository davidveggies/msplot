package org.cytoscape.msplot;

import java.util.Arrays;
import org.cytoscape.model.CyRow;
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
            // Get the first selected row
            CyRow selectedRow = e.getSource().getAllRows().stream()
                .filter(row -> row.get("selected", Boolean.class))
                .findFirst()
                .orElse(null);
                
            if (selectedRow != null) {
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
        } catch (Exception ex) {
            logger.severe("Error processing node selection: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
