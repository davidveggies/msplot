# MSPlot - Cytoscape Plugin for Mass Spectrometry Data Visualization

MSPlot is a Cytoscape app for visualizing mass spectrometry data, particularly MS2 spectra from molecular networking experiments.

## Features

- Visualize MS2 spectra by selecting nodes in a Cytoscape network
- Mirror plot view for comparing connected nodes (edges)
- Support for MGF file format with FEATURE_ID matching
- Peak labeling modes (Top K, Threshold, Hide)
- Intensity normalization
- Save plots as PNG

## Requirements

- Cytoscape 3.10.4 or later
- Java 17 or later

## Building

```bash
cd msplot
mvn clean package
```

The JAR file will be created in `msplot/target/msplot-1.0.0.jar`

## Installation

Copy the JAR file to your Cytoscape apps directory:

```bash
cp msplot/target/msplot-1.0.0.jar ~/CytoscapeConfiguration/3/apps/installed/
```

Then restart Cytoscape.

## Usage

1. Load a network in Cytoscape (XGMML format recommended)
2. Load MS2 data: In the MSPlot window, go to **File → Open MGF File**
   - The MGF file should have `FEATURE_ID=` entries matching node names in your network
3. Select nodes or edges to view MS2 spectra

### Data Format

The MGF file should follow this format:
```
BEGIN IONS
FEATURE_ID=node_name_here
PEPMASS=215.1180
RTINSECONDS=312.5
101.0239 5000.0
115.0395 8000.0
...
END IONS
```

Or use XGMML files with `ms2mzvalues` and `ms2intensities` attributes already embedded.

## Development

See `PIPELINE_README.md` for information on creating test networks from mzML files.

## License

[Add your license here]

## Author

[Add your name/contact here]

