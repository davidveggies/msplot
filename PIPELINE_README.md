# MS2 Molecular Networking Pipeline for msplot Testing

This pipeline creates a test network and MGF file from mzML files for testing the msplot Cytoscape plugin.

## Installation

First, install the required Python libraries:

```bash
cd /Users/dcraft/msplot
python3 -m pip install --user --break-system-packages -r requirements.txt
```

Or install individually:
```bash
python3 -m pip install --user --break-system-packages pymzml numpy networkx scipy
```

## Usage

```bash
python3 create_test_network.py sample_mzml_lq --num-nodes 15
```

### Options

- `input_dir`: Directory containing mzML files (required)
- `--num-nodes`: Number of nodes in network (10-100, default: 50)
- `--max-mz`: Maximum m/z value for node selection (default: 500.0)
- `--output-mgf`: Output MGF file (default: test_network.mgf)
- `--output-network`: Output network file (default: test_network.xgmml)
- `--similarity-threshold`: Minimum cosine similarity for edges (default: 0.7)
- `--intensity-ratio`: Intensity ratio for blank subtraction (default: 3.0)
- `--blank-file`: Specify blank file (auto-detected if contains "blank")

### Example

```bash
# Create network with 50 nodes (small masses, mix of connected + singletons)
python3 create_test_network.py sample_mzml_lq --num-nodes 50

# Create network with 30 nodes, only m/z < 400
python3 create_test_network.py sample_mzml_lq --num-nodes 30 --max-mz 400

# Create network with 80 nodes, lower similarity threshold
python3 create_test_network.py sample_mzml_lq --num-nodes 80 --similarity-threshold 0.6
```

## Output

The pipeline creates:

1. **test_network.mgf**: MGF file with `FEATURE_ID=` matching node names
2. **test_network.xgmml**: Cytoscape network file (XGMML format)

## Pipeline Steps

1. **Parse mzML files**: Extracts MS1 features and MS2 spectra
2. **Blank subtraction**: Removes features present in blank samples
3. **MS2 extraction**: Collects MS2 spectra for each feature
4. **Cosine similarity**: Computes MS2 spectral similarity
5. **Network creation**: Builds molecular network (top connected nodes)
6. **Export**: Creates MGF and network files

## Using in Cytoscape

1. Open `test_network.xgmml` in Cytoscape (File → Import → Network → File)
   - **The network already includes ms2mzvalues and ms2intensities columns!**
   - No need to load the MGF file separately (it's already embedded)
2. (Optional) If you want to reload/update MS2 data, use File → Open MGF File in msplot
3. Select nodes/edges to view MS2 spectra!

## Notes

- The pipeline automatically detects blank files (containing "blank" in filename)
- Features without MS2 spectra are excluded from the network
- Network nodes are selected from small m/z values (prioritizes low m/z)
- Selection includes a mix: ~70% connected nodes (in networks) + ~30% singletons (isolated)
- **XGMML file includes ms2mzvalues and ms2intensities columns directly** - ready to use!
- MGF file is also created for backup/reloading (matches msplot plugin format)

