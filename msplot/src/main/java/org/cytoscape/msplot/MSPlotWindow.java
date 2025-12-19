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
    	    "",
    	    "4. Customize View Options (under the View menu)",
    	    "   • Enable Relative Intensity Mode: scales all spectra to a max intensity of 1.",
    	    "   • Choose Peak Labeling:",
    	    "       - Top 5 or Top 10 peaks",
    	    "       - All peaks above 0.25, 0.5, or 0.75 normalized intensity",
    	    "       - Or turn labels off entirely"
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
        
        // Top-K mode
        JRadioButtonMenuItem noLabelsItem  = new JRadioButtonMenuItem("Hide all labels");
        JRadioButtonMenuItem top5Item = new JRadioButtonMenuItem("Label Top 5 Peaks");
        JRadioButtonMenuItem top10Item = new JRadioButtonMenuItem("Label Top 10 Peaks");

        // Threshold mode
        JRadioButtonMenuItem threshold25Item = new JRadioButtonMenuItem("Label Peaks > 0.25");
        JRadioButtonMenuItem threshold50Item = new JRadioButtonMenuItem("Label Peaks > 0.5");
        JRadioButtonMenuItem threshold75Item = new JRadioButtonMenuItem("Label Peaks > 0.75");

        // Add to group to enforce radio behavior
        ButtonGroup labelGroup = new ButtonGroup();
        labelGroup.add(noLabelsItem);
        labelGroup.add(top5Item);
        labelGroup.add(top10Item);
        labelGroup.add(threshold25Item);
        labelGroup.add(threshold50Item);
        labelGroup.add(threshold75Item);

        // Set default selection
        top5Item.setSelected(true);

        // Add all to menu
        labelingMenu.add(noLabelsItem);
        labelingMenu.addSeparator();        
        labelingMenu.add(top5Item);
        labelingMenu.add(top10Item);
        labelingMenu.addSeparator();
        labelingMenu.add(threshold25Item);
        labelingMenu.add(threshold50Item);
        labelingMenu.add(threshold75Item);

        // Add behavior
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
        XYBarRenderer renderer = new XYBarRenderer(0.0003); // Very thin bars to look like lines
        renderer.setBarPainter(new StandardXYBarPainter());
        renderer.setShadowVisible(false);
        renderer.setDrawBarOutline(false);
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
        
        // Add to frame
        getContentPane().add(chartPanel, BorderLayout.CENTER);
        pack();
        
        // Center relative to Cytoscape window
        setLocationRelativeTo(cySwingApplication.getJFrame());
    }
    
    public void updatePlot(double[] mzValues, double[] intensities, String featureLabel) {
        dataset.removeAllSeries();
        
        if (mzValues != null && intensities != null && mzValues.length == intensities.length) {
            XYSeries series = new XYSeries(featureLabel);
            
            double maxIntensity = 1.0;
            if (normalizeIntensities) {
                maxIntensity = Arrays.stream(intensities).max().orElse(1.0);
            }

            for (int i = 0; i < mzValues.length; i++) {
                double intensity = normalizeIntensities ? intensities[i] / maxIntensity : intensities[i];
                series.add(mzValues[i], intensity);
            }
            dataset.addSeries(series);
        }
        
        // Notify the chart that the data has changed
        chart.fireChartChanged();
    }
    
    public void updateMirrorPlot(double[] mzValues1, double[] intensities1, String nodeName1, double[] mzValues2, double[] intensities2, String nodeName2) {
        dataset.removeAllSeries();
        
        if (mzValues1 != null && intensities1 != null && mzValues1.length == intensities1.length) {
            XYSeries series1 = new XYSeries(nodeName1);
            
            double maxIntensity1 = 1.0;
            if (normalizeIntensities) {
                maxIntensity1 = Arrays.stream(intensities1).max().orElse(1.0);
            }

            for (int i = 0; i < mzValues1.length; i++) {
                double intensity = normalizeIntensities ? intensities1[i] / maxIntensity1 : intensities1[i];
                series1.add(mzValues1[i], intensity);
            }
            dataset.addSeries(series1);
        }
        
        if (mzValues2 != null && intensities2 != null && mzValues2.length == intensities2.length) {
            XYSeries series2 = new XYSeries(nodeName2);
            
            double maxIntensity2 = 1.0;
            if (normalizeIntensities) {
                maxIntensity2 = Arrays.stream(intensities2).max().orElse(1.0);
            }

            for (int i = 0; i < mzValues2.length; i++) {
                double intensity = normalizeIntensities ? intensities2[i] / maxIntensity2 : intensities2[i];
                series2.add(mzValues2[i], -intensity); // Negate for mirror plot
            }
            dataset.addSeries(series2);
        }
        
        // Set colors for the series
        XYItemRenderer renderer = plot.getRenderer();
        renderer.setSeriesPaint(0, Color.BLUE); // Blue for the top plot
        renderer.setSeriesPaint(1, Color.RED); // Red for the bottom plot
        
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
