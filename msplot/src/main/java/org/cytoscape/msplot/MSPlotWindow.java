package org.cytoscape.msplot;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JFrame;
import javax.swing.JPanel;
import org.cytoscape.application.swing.CySwingApplication;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.data.xy.XYDataset;
import org.jfree.chart.labels.StandardXYItemLabelGenerator;
import java.awt.Font;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.awt.Paint;

//adding some imports to handle file opening for mgf file
import javax.swing.JMenuBar;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JFileChooser;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import org.cytoscape.model.CyNetwork;
import java.util.Arrays;
import javax.swing.ButtonGroup;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JCheckBoxMenuItem;
import org.jfree.chart.ChartUtils;
import java.io.IOException;
import org.jfree.chart.entity.ChartEntity;
import org.jfree.chart.entity.XYItemEntity;
import java.awt.event.MouseEvent;
import javax.swing.JPopupMenu;
import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;

public class MSPlotWindow extends JFrame {
    private final CyNetwork network;
    private final XYSeriesCollection dataset;
    private final JFreeChart chart;
    private final ChartPanel chartPanel;
    private final XYPlot plot;
    
    // Switch variable to control labeling mode
    private boolean labelAllPeaksAboveThreshold = true; // Default mode
    private int topK = 5; // Default top K peaks
    private double threshold = 0.5; // Default threshold for labeling
    private boolean normalizeIntensities = false; // Default to raw intensities
    
    // Neutral loss mode: store original m/z values and reference points
    private double[] originalMzValues = null;  // For single plot
    private double[] originalIntensities = null; // For single plot
    private double[] originalMzValues1 = null; // For mirror plot series 1
    private double[] originalIntensities1 = null; // For mirror plot series 1
    private double[] originalMzValues2 = null; // For mirror plot series 2
    private double[] originalIntensities2 = null; // For mirror plot series 2
    private String currentFeatureLabel = null; // Current feature label for single plot
    private String currentNodeName1 = null; // Current node names for mirror plot
    private String currentNodeName2 = null;
    private Double referenceMz = null;  // Reference m/z for neutral loss (null = disabled)
    private Double referenceMz1 = null; // Reference m/z for mirror plot series 1
    private Double referenceMz2 = null; // Reference m/z for mirror plot series 2
    private Integer referenceItemIndex = null; // Item index of reference peak for single plot
    private Integer referenceItemIndex1 = null; // Item index of reference peak for series 1
    private Integer referenceItemIndex2 = null; // Item index of reference peak for series 2
    private boolean isMirrorPlot = false; // Track if we're in mirror plot mode
    private static final String HELP_TEXT = String.join("\n",
    	    "MS Plot Plugin",
    	    "",
    	    "1. Load Your Cytoscape Network",
    	    "   • Open your Cytoscape .cys file, typically from a GNPS Feature-Based Molecular Networking (FBMN) run.",
    	    "",
    	    "2. Load the Corresponding MGF File",
    	    "   • From the same GNPS job, load in the fbmn.mgf (sirius) file:",
    	    "   • You only need to do this once — after saving the .cys file, the MGF data is retained.",
    	    "",
    	    "3. MS2 Spectra",
    	    "   • Select a node to view its MS2 spectrum.",
    	    "   • Select an edge to view a mirror plot comparing the two connected nodes.",
    	    "   • Select two nodes (shift-click) to view a mirror plot.",
    	    "",
    	    "4. Customize View Options (under the View menu)",
    	    "   • Enable Relative Intensity Mode: scales all spectra to a max intensity of 1.",
    	    "   • Choose Peak Labeling:",
    	    "       - Top 5 or Top 10 peaks",
    	    "       - All peaks above 0.25, 0.5, or 0.75 normalized intensity",
    	    "       - Or turn labels off entirely",
    	    "",
    	    "5. Neutral Loss View",
    	    "   • Click on any peak to set it as the zero point (typically the precursor ion).",
    	    "   • All other peaks will be labeled as neutral losses (m/z difference).",
    	    "   • The zero point peak is displayed with a dashed outline for visual indication.",
    	    "   • To reset: View menu → Reset Zero Point, or right-click a peak → Clear Zero Point.",
    	    "   • Neutral loss view automatically resets when selecting a different node."
    	);
    
    public MSPlotWindow(CySwingApplication cySwingApplication, CyNetwork network) {
        super("MS Plot");
        this.network = network;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        
        // Create the menu bar for file selection
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem openMenuItem = new JMenuItem("Open MGF File");
        JMenuItem saveAsPNGItem = new JMenuItem("Save as PNG");

     // Create View menu
        JMenu viewMenu = new JMenu("View");
     // Create Labeling Mode Submenu
        JMenu labelingMenu = new JMenu("Labeling Mode");

        
        // Create the dataset and chart
        dataset = new XYSeriesCollection();
        chart = ChartFactory.createXYLineChart(
            "Mass Spectrum",      // chart title
            "m/z",               // x axis label
            "Intensity",         // y axis label
            dataset,              // data
            PlotOrientation.VERTICAL,
            true,                 // include legend
            true,                 // tooltips
            false                 // urls
        );
        
        // Labeling options
        JRadioButtonMenuItem viewAllPeaksItem = new JRadioButtonMenuItem("View All Peaks");
        JRadioButtonMenuItem noLabelsItem  = new JRadioButtonMenuItem("Hide all labels");
        JRadioButtonMenuItem top5Item = new JRadioButtonMenuItem("Label Top 5 Peaks");
        JRadioButtonMenuItem top10Item = new JRadioButtonMenuItem("Label Top 10 Peaks");

        // Threshold mode
        JRadioButtonMenuItem threshold25Item = new JRadioButtonMenuItem("Label Peaks > 0.25");
        JRadioButtonMenuItem threshold50Item = new JRadioButtonMenuItem("Label Peaks > 0.5");
        JRadioButtonMenuItem threshold75Item = new JRadioButtonMenuItem("Label Peaks > 0.75");

        // Add to group to enforce radio behavior
        ButtonGroup labelGroup = new ButtonGroup();
        labelGroup.add(viewAllPeaksItem);
        labelGroup.add(noLabelsItem);
        labelGroup.add(top5Item);
        labelGroup.add(top10Item);
        labelGroup.add(threshold25Item);
        labelGroup.add(threshold50Item);
        labelGroup.add(threshold75Item);

        // Set default selection
        top5Item.setSelected(true);

        // Add all to menu (viewAllPeaksItem at the top)
        labelingMenu.add(viewAllPeaksItem);
        labelingMenu.addSeparator();
        labelingMenu.add(noLabelsItem);
        labelingMenu.addSeparator();        
        labelingMenu.add(top5Item);
        labelingMenu.add(top10Item);
        labelingMenu.addSeparator();
        labelingMenu.add(threshold25Item);
        labelingMenu.add(threshold50Item);
        labelingMenu.add(threshold75Item);

        // Add behavior
        viewAllPeaksItem.addActionListener(e -> {
            labelAllPeaksAboveThreshold = true;
            threshold = 0.0; // Show all peaks (any peak > 0)
            chart.fireChartChanged();
        });
        
        noLabelsItem.addActionListener(e -> {
            if (labelAllPeaksAboveThreshold) {
                threshold = 1.0;
            } else {
                topK = 0;
            }
            chart.fireChartChanged();
        });
        
        top5Item.addActionListener(e -> {
            labelAllPeaksAboveThreshold = false;
            topK = 5;
            chart.fireChartChanged();
        });

        top10Item.addActionListener(e -> {
            labelAllPeaksAboveThreshold = false;
            topK = 10;
            chart.fireChartChanged();
        });

        threshold25Item.addActionListener(e -> {
            labelAllPeaksAboveThreshold = true;
            threshold = 0.25;
            chart.fireChartChanged();
        });

        threshold50Item.addActionListener(e -> {
            labelAllPeaksAboveThreshold = true;
            threshold = 0.5;
            chart.fireChartChanged();
        });

        threshold75Item.addActionListener(e -> {
            labelAllPeaksAboveThreshold = true;
            threshold = 0.75;
            chart.fireChartChanged();
        });
        
        
        openMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                int returnValue = fileChooser.showOpenDialog(null);
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    File selectedFile = fileChooser.getSelectedFile();
                    MGFFileReader.readMGFFile(selectedFile.getAbsolutePath(), network);
                }
            }
        });

        saveAsPNGItem.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            int returnValue = fileChooser.showSaveDialog(this);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                saveChartAsPNG(selectedFile);
            }
        });

        fileMenu.add(openMenuItem);
        fileMenu.add(saveAsPNGItem);
        menuBar.add(fileMenu);
        
        viewMenu.add(labelingMenu);

        JCheckBoxMenuItem normalizeMenuItem = new JCheckBoxMenuItem("Normalize Intensities");
        normalizeMenuItem.addActionListener(e -> {
            normalizeIntensities = normalizeMenuItem.isSelected();
            chart.fireChartChanged(); // Refresh the chart
        });
        viewMenu.add(normalizeMenuItem);
        viewMenu.addSeparator();
        
        // Add menu item to reset neutral loss zero point (make it more visible)
        JMenuItem resetZeroPointItem = new JMenuItem("Reset Zero Point");
        resetZeroPointItem.setToolTipText("Clears the zero point and returns to absolute m/z view. Also accessible via right-click on peak.");
        resetZeroPointItem.addActionListener(e -> {
            referenceMz = null;
            referenceMz1 = null;
            referenceMz2 = null;
            referenceItemIndex = null;
            referenceItemIndex1 = null;
            referenceItemIndex2 = null;
            refreshPlot(); // Rebuild plot with original m/z values
        });
        viewMenu.add(resetZeroPointItem);
        
        menuBar.add(viewMenu);

        // Create Help menu
        JMenu helpMenu = new JMenu("Help");
        JMenuItem helpItem = new JMenuItem("How to Use");

        helpItem.addActionListener(e -> {
            JOptionPane.showMessageDialog(
                this,
                HELP_TEXT,
                "MS Plot - Help",
                JOptionPane.INFORMATION_MESSAGE
            );
        });

        helpMenu.add(helpItem);
        menuBar.add(helpMenu);
        
        // Debug: verify menu bar has all menus
        System.out.println("MSPlot: Menu bar has " + menuBar.getMenuCount() + " menus");
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            System.out.println("  Menu " + i + ": " + menuBar.getMenu(i).getText());
        }
        
        // Set the menu bar after all menus are added
        try {
            setJMenuBar(menuBar);
            System.out.println("MSPlot: Menu bar set successfully");
            // Force menu bar to be visible (important on macOS)
            menuBar.setVisible(true);
            // On macOS, ensure the menu bar is shown
            if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                System.out.println("MSPlot: macOS detected, ensuring menu bar visibility");
            }
        } catch (Exception e) {
            System.err.println("MSPlot: Error setting menu bar: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Customize the plot
        plot = (XYPlot) chart.getPlot();
        
        // Use XYBarRenderer with very thin bars to create vertical lines
        XYBarRenderer renderer = new XYBarRenderer(0.0003) {
            @Override
            public java.awt.Stroke getItemOutlineStroke(int row, int column) {
                // Use dashed stroke for reference peak (zero point), solid for others
                if (isReferencePeak(row, column)) {
                    float[] dash = {5.0f, 5.0f};
                    return new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dash, 0.0f);
                }
                return super.getItemOutlineStroke(row, column);
            }
            
            @Override
            public java.awt.Paint getItemOutlinePaint(int row, int column) {
                // Use darker color for reference peak outline
                if (isReferencePeak(row, column)) {
                    return Color.DARK_GRAY;
                }
                return super.getItemOutlinePaint(row, column);
            }
            
            private boolean isReferencePeak(int series, int item) {
                if (isMirrorPlot) {
                    if (series == 0 && referenceItemIndex1 != null && referenceItemIndex1 == item) {
                        return true;
                    } else if (series == 1 && referenceItemIndex2 != null && referenceItemIndex2 == item) {
                        return true;
                    }
                } else {
                    if (referenceItemIndex != null && referenceItemIndex == item) {
                        return true;
                    }
                }
                return false;
            }
        };
        renderer.setBarPainter(new StandardXYBarPainter());
        renderer.setShadowVisible(false);
        renderer.setDrawBarOutline(true); // Enable outlines so dashed stroke is visible
        renderer.setMargin(0.2); // No margin between bars
        
        // Label peaks based on the switch variable
        renderer.setDefaultItemLabelGenerator(new StandardXYItemLabelGenerator() {
            @Override
            public String generateLabel(XYDataset dataset, int series, int item) {
                double mz = dataset.getXValue(series, item);
                double intensity = dataset.getYValue(series, item);

                if (labelAllPeaksAboveThreshold) {
                    // Normalize intensities for threshold comparison
                    double maxIntensity = getMaxIntensity(dataset, series);
                    double normalizedIntensity = intensity / maxIntensity;
                    // Label all peaks above the normalized threshold
                    return normalizedIntensity > threshold ? String.format("%.2f", mz) : null;
                } else {
                    // Label the top K highest peaks
                    return isTopKPeak(dataset, series, item) ? String.format("%.2f", mz) : null;
                }
            }
        });
        
        renderer.setDefaultItemLabelsVisible(true);
        renderer.setDefaultItemLabelFont(new Font("SansSerif", Font.PLAIN, 10));
        plot.setRenderer(renderer);
        
        // Set the range axis to start at zero
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setAutoRangeIncludesZero(true);
        
        // Create the chart panel
        chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(600, 400));
        chartPanel.setMouseZoomable(false); // Disable zoom to allow clicking
        
        // Add mouse listener for peak clicking
        chartPanel.addChartMouseListener(new ChartMouseListener() {
            @Override
            public void chartMouseClicked(ChartMouseEvent event) {
                handleChartMouseClick(event);
            }
            
            @Override
            public void chartMouseMoved(ChartMouseEvent event) {
                // Optional: could show tooltip or highlight on hover
            }
        });
        
        // Add to frame
        getContentPane().add(chartPanel, BorderLayout.CENTER);
        pack();
        
        // Center relative to Cytoscape window
        setLocationRelativeTo(cySwingApplication.getJFrame());
    }
    
    public void updatePlot(double[] mzValues, double[] intensities, String featureLabel) {
        updatePlot(mzValues, intensities, featureLabel, true);
    }
    
    private void updatePlot(double[] mzValues, double[] intensities, String featureLabel, boolean resetReference) {
        // Reset neutral loss when switching to a different node (but not when refreshing)
        if (resetReference) {
            referenceMz = null;
            referenceItemIndex = null;
        }
        
        // Store original m/z values and intensities for neutral loss calculation
        originalMzValues = mzValues != null ? Arrays.copyOf(mzValues, mzValues.length) : null;
        originalIntensities = intensities != null ? Arrays.copyOf(intensities, intensities.length) : null;
        originalMzValues1 = null;
        originalIntensities1 = null;
        originalMzValues2 = null;
        originalIntensities2 = null;
        currentFeatureLabel = featureLabel;
        currentNodeName1 = null;
        currentNodeName2 = null;
        isMirrorPlot = false;
        
        dataset.removeAllSeries();
        
        if (mzValues != null && intensities != null && mzValues.length == intensities.length) {
            XYSeries series = new XYSeries(featureLabel);
            
            double maxIntensity = 1.0;
            if (normalizeIntensities) {
                maxIntensity = Arrays.stream(intensities).max().orElse(1.0);
            }

            // Calculate displayed m/z values (neutral loss if reference is set)
            double[] displayMzValues = calculateDisplayMzValues(mzValues, referenceMz);
            
            // Find reference peak index
            referenceItemIndex = null;
            if (referenceMz != null && mzValues != null) {
                for (int i = 0; i < mzValues.length; i++) {
                    if (Math.abs(mzValues[i] - referenceMz) < 0.001) {
                        referenceItemIndex = i;
                        break;
                    }
                }
            }

            for (int i = 0; i < displayMzValues.length; i++) {
                double intensity = normalizeIntensities ? intensities[i] / maxIntensity : intensities[i];
                series.add(displayMzValues[i], intensity);
            }
            dataset.addSeries(series);
        }
        
        // Update x-axis label based on neutral loss mode
        updateXAxisLabel();
        
        // Notify the chart that the data has changed
        chart.fireChartChanged();
    }
    
    public void updateMirrorPlot(double[] mzValues1, double[] intensities1, String nodeName1, double[] mzValues2, double[] intensities2, String nodeName2) {
        updateMirrorPlot(mzValues1, intensities1, nodeName1, mzValues2, intensities2, nodeName2, true);
    }
    
    private void updateMirrorPlot(double[] mzValues1, double[] intensities1, String nodeName1, double[] mzValues2, double[] intensities2, String nodeName2, boolean resetReference) {
        // Reset neutral loss when switching to different nodes (but not when refreshing)
        if (resetReference) {
            referenceMz1 = null;
            referenceMz2 = null;
            referenceItemIndex1 = null;
            referenceItemIndex2 = null;
        }
        
        // Store original m/z values and intensities for neutral loss calculation
        originalMzValues = null;
        originalIntensities = null;
        originalMzValues1 = mzValues1 != null ? Arrays.copyOf(mzValues1, mzValues1.length) : null;
        originalIntensities1 = intensities1 != null ? Arrays.copyOf(intensities1, intensities1.length) : null;
        originalMzValues2 = mzValues2 != null ? Arrays.copyOf(mzValues2, mzValues2.length) : null;
        originalIntensities2 = intensities2 != null ? Arrays.copyOf(intensities2, intensities2.length) : null;
        currentFeatureLabel = null;
        currentNodeName1 = nodeName1;
        currentNodeName2 = nodeName2;
        isMirrorPlot = true;
        
        dataset.removeAllSeries();
        
        if (mzValues1 != null && intensities1 != null && mzValues1.length == intensities1.length) {
            XYSeries series1 = new XYSeries(nodeName1);
            
            double maxIntensity1 = 1.0;
            if (normalizeIntensities) {
                maxIntensity1 = Arrays.stream(intensities1).max().orElse(1.0);
            }

            // Calculate displayed m/z values (neutral loss if reference is set)
            double[] displayMzValues1 = calculateDisplayMzValues(mzValues1, referenceMz1);
            
            // Find reference peak index for series 1
            referenceItemIndex1 = null;
            if (referenceMz1 != null && mzValues1 != null) {
                for (int i = 0; i < mzValues1.length; i++) {
                    if (Math.abs(mzValues1[i] - referenceMz1) < 0.001) {
                        referenceItemIndex1 = i;
                        break;
                    }
                }
            }

            for (int i = 0; i < displayMzValues1.length; i++) {
                double intensity = normalizeIntensities ? intensities1[i] / maxIntensity1 : intensities1[i];
                series1.add(displayMzValues1[i], intensity);
            }
            dataset.addSeries(series1);
        }
        
        if (mzValues2 != null && intensities2 != null && mzValues2.length == intensities2.length) {
            XYSeries series2 = new XYSeries(nodeName2);
            
            double maxIntensity2 = 1.0;
            if (normalizeIntensities) {
                maxIntensity2 = Arrays.stream(intensities2).max().orElse(1.0);
            }

            // Calculate displayed m/z values (neutral loss if reference is set)
            double[] displayMzValues2 = calculateDisplayMzValues(mzValues2, referenceMz2);
            
            // Find reference peak index for series 2
            referenceItemIndex2 = null;
            if (referenceMz2 != null && mzValues2 != null) {
                for (int i = 0; i < mzValues2.length; i++) {
                    if (Math.abs(mzValues2[i] - referenceMz2) < 0.001) {
                        referenceItemIndex2 = i;
                        break;
                    }
                }
            }

            for (int i = 0; i < displayMzValues2.length; i++) {
                double intensity = normalizeIntensities ? intensities2[i] / maxIntensity2 : intensities2[i];
                series2.add(displayMzValues2[i], -intensity); // Negate for mirror plot
            }
            dataset.addSeries(series2);
        }
        
        // Set colors for the series
        XYItemRenderer renderer = plot.getRenderer();
        renderer.setSeriesPaint(0, Color.BLUE); // Blue for the top plot
        renderer.setSeriesPaint(1, Color.RED); // Red for the bottom plot
        
        // Update x-axis label based on neutral loss mode
        updateXAxisLabel();
        
        // Notify the chart that the data has changed
        chart.fireChartChanged();
    }
    // Method to determine if a peak is among the top K
    private boolean isTopKPeak(XYDataset dataset, int series, int item) {
        double[] intensities = new double[dataset.getItemCount(series)];
        for (int i = 0; i < intensities.length; i++) {
            intensities[i] = dataset.getYValue(series, i);
        }
        Arrays.sort(intensities);
        double thresholdIntensity = intensities[Math.max(0, intensities.length - topK)];
        return dataset.getYValue(series, item) >= thresholdIntensity;
    }

    // Method to get the maximum intensity for normalization
    private double getMaxIntensity(XYDataset dataset, int series) {
        double maxIntensity = 0;
        for (int i = 0; i < dataset.getItemCount(series); i++) {
            maxIntensity = Math.max(maxIntensity, dataset.getYValue(series, i));
        }
        return maxIntensity;
    }

    // Handle mouse click on chart to show menu for peak options
    private void handleChartMouseClick(ChartMouseEvent event) {
        MouseEvent mouseEvent = event.getTrigger();
        ChartEntity entity = event.getEntity();
        
        // Check if we clicked on a chart element (peak)
        if (entity instanceof XYItemEntity) {
            XYItemEntity itemEntity = (XYItemEntity) entity;
            int series = itemEntity.getSeriesIndex();
            int item = itemEntity.getItem();
            
            // Get the original m/z value directly using the item index
            double originalMz = getOriginalMzByIndex(series, item);
            
            if (originalMz > 0) {
                // Show popup menu at click location
                showPeakClickMenu(mouseEvent.getX(), mouseEvent.getY(), originalMz, series, item);
            }
        }
    }
    
    // Get original m/z value by series and item index
    private double getOriginalMzByIndex(int series, int item) {
        if (isMirrorPlot) {
            if (series == 0 && originalMzValues1 != null && item < originalMzValues1.length) {
                return originalMzValues1[item];
            } else if (series == 1 && originalMzValues2 != null && item < originalMzValues2.length) {
                return originalMzValues2[item];
            }
        } else {
            if (originalMzValues != null && item < originalMzValues.length) {
                return originalMzValues[item];
            }
        }
        return -1; // Error
    }
    
    // Show popup menu when a peak is clicked (framework for future options)
    private void showPeakClickMenu(int x, int y, double mzValue, int series, int item) {
        JPopupMenu popupMenu = new JPopupMenu();
        
        // Main action: Set as zero point for neutral loss
        JMenuItem setZeroPointItem = new JMenuItem("Set as Neutral Loss Reference");
        setZeroPointItem.addActionListener(e -> {
            // Set as zero point
            if (isMirrorPlot) {
                if (series == 0) {
                    referenceMz1 = mzValue;
                    referenceItemIndex1 = item;
                } else if (series == 1) {
                    referenceMz2 = mzValue;
                    referenceItemIndex2 = item;
                }
            } else {
                referenceMz = mzValue;
                referenceItemIndex = item;
            }
            
            // Refresh the plot to show neutral losses
            refreshPlot();
        });
        popupMenu.add(setZeroPointItem);
        
        // Option to clear zero point (only show if one is already set)
        boolean hasReference = (isMirrorPlot && ((series == 0 && referenceMz1 != null) || (series == 1 && referenceMz2 != null))) ||
                               (!isMirrorPlot && referenceMz != null);
        
        if (hasReference) {
            popupMenu.addSeparator();
            JMenuItem clearZeroPointItem = new JMenuItem("Clear Zero Point");
            clearZeroPointItem.addActionListener(e -> {
                if (isMirrorPlot) {
                    if (series == 0) {
                        referenceMz1 = null;
                        referenceItemIndex1 = null;
                    } else if (series == 1) {
                        referenceMz2 = null;
                        referenceItemIndex2 = null;
                    }
                } else {
                    referenceMz = null;
                    referenceItemIndex = null;
                }
                refreshPlot();
            });
            popupMenu.add(clearZeroPointItem);
        }
        
        // Future options can be added here
        // popupMenu.addSeparator();
        // popupMenu.add(new JMenuItem("Future Option 1"));
        // popupMenu.add(new JMenuItem("Future Option 2"));
        
        // Show menu at click location
        popupMenu.show(chartPanel, x, y);
    }
    
    // Calculate display m/z values (neutral loss if reference is set)
    private double[] calculateDisplayMzValues(double[] mzValues, Double reference) {
        if (reference == null || mzValues == null) {
            return mzValues != null ? Arrays.copyOf(mzValues, mzValues.length) : null;
        }
        
        double[] result = new double[mzValues.length];
        for (int i = 0; i < mzValues.length; i++) {
            result[i] = mzValues[i] - reference; // Neutral loss
        }
        return result;
    }
    
    // Update x-axis label based on neutral loss mode
    private void updateXAxisLabel() {
        NumberAxis domainAxis = (NumberAxis) plot.getDomainAxis();
        if ((!isMirrorPlot && referenceMz != null) || 
            (isMirrorPlot && (referenceMz1 != null || referenceMz2 != null))) {
            domainAxis.setLabel("Neutral Loss (m/z)");
        } else {
            domainAxis.setLabel("m/z");
        }
    }
    
    // Refresh the plot with current data and neutral loss settings
    private void refreshPlot() {
        // Rebuild the plot using stored data (don't reset references - preserve current neutral loss settings)
        if (isMirrorPlot && originalMzValues1 != null && originalIntensities1 != null && 
            originalMzValues2 != null && originalIntensities2 != null && 
            currentNodeName1 != null && currentNodeName2 != null) {
            updateMirrorPlot(originalMzValues1, originalIntensities1, currentNodeName1,
                           originalMzValues2, originalIntensities2, currentNodeName2, false);
        } else if (!isMirrorPlot && originalMzValues != null && originalIntensities != null && currentFeatureLabel != null) {
            updatePlot(originalMzValues, originalIntensities, currentFeatureLabel, false);
        }
    }
    
    // Method to save the chart as a PNG file
    public void saveChartAsPNG(File file) {
        try {
        	// Save original settings
        	Paint originalBackground = chart.getPlot().getBackgroundPaint();
        	Paint originalChartBackground = chart.getBackgroundPaint();

        	chart.setBackgroundPaint(new Color(255, 255, 255, 0)); // Fully transparent background

        	if (chart.getPlot() != null) {
        	    chart.getPlot().setBackgroundPaint(new Color(255, 255, 255, 0)); // Transparent plot area
        	}
        	
            // Add .png extension if missing
            if (!file.getName().toLowerCase().endsWith(".png")) {
                file = new File(file.getAbsolutePath() + ".png");
            }
            ChartUtils.saveChartAsPNG(file, chart, 800, 600); // Specify the width and height
            // Restore original settings
            chart.getPlot().setBackgroundPaint(originalBackground);
            chart.setBackgroundPaint(originalChartBackground);
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving chart as PNG: " + e.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
