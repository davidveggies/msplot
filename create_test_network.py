#!/usr/bin/env python3
"""
Pipeline to create a test network and MGF file from mzML files for msplot plugin testing.

This script:
1. Reads mzML files (samples + blank)
2. Performs feature detection and blank subtraction
3. Extracts MS2 spectra
4. Computes MS2 cosine similarity
5. Creates a network (10-20 nodes)
6. Exports MGF file (with FEATURE_ID matching node names)
7. Exports Cytoscape network file (.xgmml)

Usage:
    python3 create_test_network.py sample_mzml_lq --num-nodes 15
"""

import sys
import os
import argparse
from pathlib import Path
import numpy as np
from collections import defaultdict
import math
import zipfile
import xml.etree.ElementTree as ET
from xml.dom import minidom
from datetime import datetime
import tempfile

try:
    import pymzml
except ImportError:
    print("Error: pymzml not installed. Install with: pip install pymzml")
    print("Or install all requirements: pip install -r requirements.txt")
    sys.exit(1)

try:
    import networkx as nx
except ImportError:
    print("Error: networkx not installed. Install with: pip install networkx")
    sys.exit(1)

try:
    from scipy.spatial.distance import cosine
except ImportError:
    print("Error: scipy not installed. Install with: pip install scipy")
    sys.exit(1)


def cosine_similarity_ms2(spectrum1, spectrum2, mz_tolerance=0.02):
    """
    Compute cosine similarity between two MS2 spectra.
    Spectra are lists of (mz, intensity) tuples.
    """
    if not spectrum1 or not spectrum2:
        return 0.0
    
    # Align spectra by m/z (within tolerance)
    aligned1 = []
    aligned2 = []
    
    i, j = 0, 0
    while i < len(spectrum1) and j < len(spectrum2):
        mz1, int1 = spectrum1[i]
        mz2, int2 = spectrum2[j]
        
        if abs(mz1 - mz2) <= mz_tolerance:
            # Match found - use average m/z
            avg_mz = (mz1 + mz2) / 2
            aligned1.append((avg_mz, int1))
            aligned2.append((avg_mz, int2))
            i += 1
            j += 1
        elif mz1 < mz2:
            aligned1.append((mz1, int1))
            aligned2.append((mz1, 0.0))
            i += 1
        else:
            aligned1.append((mz2, 0.0))
            aligned2.append((mz2, int2))
            j += 1
    
    # Add remaining peaks
    while i < len(spectrum1):
        aligned1.append(spectrum1[i])
        aligned2.append((spectrum1[i][0], 0.0))
        i += 1
    while j < len(spectrum2):
        aligned1.append((spectrum2[j][0], 0.0))
        aligned2.append(spectrum2[j])
        j += 1
    
    # Extract intensity vectors
    int1 = np.array([x[1] for x in aligned1])
    int2 = np.array([x[1] for x in aligned2])
    
    # Compute cosine similarity
    dot_product = np.dot(int1, int2)
    norm1 = np.linalg.norm(int1)
    norm2 = np.linalg.norm(int2)
    
    if norm1 == 0 or norm2 == 0:
        return 0.0
    
    return dot_product / (norm1 * norm2)


def extract_features_from_mzml(mzml_path, is_blank=False):
    """
    Extract MS1 features and MS2 spectra from mzML file.
    Returns: (features_dict, ms2_spectra_dict)
    features_dict: {feature_id: {'mz': float, 'rt': float, 'intensity': float, 'file': str}}
    ms2_spectra_dict: {feature_id: [(mz, intensity), ...]}
    """
    print(f"Processing {mzml_path.name}...")
    
    features = {}
    ms2_spectra = {}
    
    try:
        run = pymzml.run.Reader(str(mzml_path))
        
        feature_counter = 0
        ms2_count = 0
        total_ms2_spectra = 0  # Debug counter
        
        for spectrum in run:
            ms_level = spectrum.get('ms level', 0)
            rt = spectrum.get('scan time', None)  # in minutes
            if rt is None:
                rt = spectrum.get('scan time in seconds', None)
                if rt is not None:
                    rt = rt / 60.0  # Convert to minutes
            
            if ms_level == 1:
                # MS1 spectrum - extract features (peaks)
                peaks = spectrum.peaks('raw')
                if peaks is not None and len(peaks) > 0:
                    # Find base peak (highest intensity)
                    base_peak = max(peaks, key=lambda x: x[1] if len(x) > 1 else 0)
                    if len(base_peak) >= 2 and base_peak[1] > 0:
                        feature_id = f"feature_{mzml_path.stem}_{feature_counter:04d}"
                        feature_counter += 1
                        
                        features[feature_id] = {
                            'mz': float(base_peak[0]),
                            'rt': rt if rt else 0.0,
                            'intensity': float(base_peak[1]),
                            'file': mzml_path.stem,
                            'is_blank': is_blank
                        }
            
            elif ms_level == 2:
                total_ms2_spectra += 1  # Debug: count all MS2 spectra
                # MS2 spectrum - extract precursor m/z
                # In pymzml, precursor info is typically in selected_precursors
                precursor_mz = None
                
                try:
                    # Method 1: Access via selected_precursors (most common)
                    if hasattr(spectrum, 'selected_precursors'):
                        selected = spectrum.selected_precursors
                        if selected and len(selected) > 0:
                            prec = selected[0]
                            if isinstance(prec, dict):
                                precursor_mz = prec.get('mz', None)
                                if precursor_mz is None:
                                    # Try 'selected ion m/z' key
                                    precursor_mz = prec.get('selected ion m/z', None)
                            elif hasattr(prec, 'mz'):
                                precursor_mz = prec.mz
                            elif hasattr(prec, 'selected_ion_mz'):
                                precursor_mz = prec.selected_ion_mz
                    
                    # Method 2: Direct precursors attribute
                    if precursor_mz is None and hasattr(spectrum, 'precursors'):
                        prec_list = spectrum.precursors
                        if prec_list and len(prec_list) > 0:
                            prec = prec_list[0]
                            if isinstance(prec, dict):
                                precursor_mz = prec.get('mz', None)
                            elif hasattr(prec, 'mz'):
                                precursor_mz = prec.mz
                    
                    # Method 3: Dictionary-style access to spectrum metadata
                    if precursor_mz is None:
                        for key in ['precursor m/z', 'selected ion m/z', 'base peak m/z']:
                            val = spectrum.get(key, None)
                            if val is not None:
                                try:
                                    precursor_mz = float(val)
                                    break
                                except (ValueError, TypeError):
                                    continue
                except (AttributeError, IndexError, TypeError, ValueError) as e:
                    # Skip this spectrum if we can't get precursor
                    continue
                
                if precursor_mz is None:
                    continue
                
                # Extract MS2 peaks
                peaks = spectrum.peaks('raw')
                if peaks is None or len(peaks) == 0:
                    continue
                
                # Filter low intensity peaks (top 50 or intensity > 1% of max)
                max_intensity = max(p[1] for p in peaks if len(p) > 1)
                threshold = max(0.01 * max_intensity, max_intensity / 50.0)
                filtered_peaks = [(float(p[0]), float(p[1])) for p in peaks 
                                if len(p) > 1 and p[1] >= threshold]
                
                if not filtered_peaks:
                    continue
                
                # Find closest feature by precursor m/z (within tolerance)
                best_feature = None
                min_diff = float('inf')
                mz_tolerance = 0.1  # 0.1 Da tolerance
                
                for fid, feat in features.items():
                    if not feat.get('is_blank', False):
                        diff = abs(feat['mz'] - precursor_mz)
                        if diff < min_diff and diff < mz_tolerance:
                            min_diff = diff
                            best_feature = fid
                
                if best_feature:
                    # Add MS2 to existing feature
                    ms2_spectra[best_feature] = filtered_peaks
                    ms2_count += 1
                elif not is_blank:
                    # Create new feature for this MS2 (only if not blank)
                    feature_id = f"feature_{mzml_path.stem}_ms2_{ms2_count:04d}"
                    features[feature_id] = {
                        'mz': float(precursor_mz),
                        'rt': rt if rt else 0.0,
                        'intensity': sum(p[1] for p in filtered_peaks),
                        'file': mzml_path.stem,
                        'is_blank': is_blank
                    }
                    ms2_spectra[feature_id] = filtered_peaks
                    ms2_count += 1
        
        print(f"  Extracted {len(features)} features, {ms2_count} with MS2 spectra (found {total_ms2_spectra} total MS2 spectra in file)")
        
    except Exception as e:
        print(f"  Error processing {mzml_path}: {e}")
        import traceback
        traceback.print_exc()
    
    return features, ms2_spectra


def blank_subtraction(sample_features, blank_features, intensity_ratio_threshold=3.0):
    """
    Remove features that appear in blank (with similar intensity).
    Returns filtered features dict.
    """
    print(f"Blank subtraction: {len(sample_features)} sample features, {len(blank_features)} blank features")
    
    filtered = {}
    removed = 0
    
    for fid, feat in sample_features.items():
        mz = feat['mz']
        rt = feat['rt']
        intensity = feat['intensity']
        
        # Check if similar feature exists in blank
        found_in_blank = False
        for blank_fid, blank_feat in blank_features.items():
            mz_diff = abs(blank_feat['mz'] - mz)
            rt_diff = abs(blank_feat['rt'] - rt) if rt and blank_feat['rt'] else 0
            
            # Within 0.01 Da and 0.1 min
            if mz_diff < 0.01 and rt_diff < 0.1:
                # Check intensity ratio
                if blank_feat['intensity'] > 0:
                    ratio = intensity / blank_feat['intensity']
                    if ratio < intensity_ratio_threshold:
                        found_in_blank = True
                        removed += 1
                        break
        
        if not found_in_blank:
            filtered[fid] = feat
    
    print(f"  Removed {removed} blank features, {len(filtered)} remaining")
    return filtered


def create_network(features, ms2_spectra, num_nodes=50, similarity_threshold=0.7, max_mz=500.0):
    """
    Create a molecular network based on MS2 cosine similarity.
    Selects nodes with small m/z values, including both connected nodes and singletons.
    Returns: NetworkX graph with selected nodes (mix of connected and singleton).
    """
    print(f"\nCreating network from {len(features)} features...")
    
    # Filter to features with MS2 data and small m/z
    features_with_ms2_all = {fid: feat for fid, feat in features.items() if fid in ms2_spectra}
    
    # Show m/z statistics
    if features_with_ms2_all:
        mz_values = [feat.get('mz', 0) for feat in features_with_ms2_all.values()]
        mz_values.sort()
        print(f"  {len(features_with_ms2_all)} features have MS2 spectra")
        print(f"  m/z range: {mz_values[0]:.2f} - {mz_values[-1]:.2f} (median: {mz_values[len(mz_values)//2]:.2f})")
    else:
        print(f"  WARNING: No features have MS2 spectra after blank subtraction!")
        return nx.Graph(), {}
    
    # Filter by max_mz
    features_with_ms2 = {fid: feat for fid, feat in features_with_ms2_all.items() 
                        if feat.get('mz', float('inf')) <= max_mz}
    print(f"  {len(features_with_ms2)} features have MS2 spectra and m/z <= {max_mz}")
    
    if len(features_with_ms2) == 0:
        print(f"  ERROR: No features meet m/z <= {max_mz} criterion!")
        print(f"  Try increasing --max-mz (current: {max_mz})")
        print(f"  Suggested: --max-mz {min(2000.0, max(500.0, mz_values[-1] * 1.1)):.0f}")
        return nx.Graph(), {}
    
    if len(features_with_ms2) < num_nodes:
        print(f"  Note: Only {len(features_with_ms2)} features meet m/z <= {max_mz} criterion")
        if len(features_with_ms2) == 0:
            print(f"  ERROR: No features meet m/z <= {max_mz} criterion!")
            print(f"  Try increasing --max-mz (current: {max_mz})")
            if mz_values:
                suggested_max = min(2000.0, max(500.0, mz_values[-1] * 1.1))
                print(f"  Suggested: --max-mz {suggested_max:.0f}")
            return nx.Graph(), {}
        num_nodes = len(features_with_ms2)
        print(f"  Using all {num_nodes} features that meet criteria")
    
    # Sort features by m/z (smallest first)
    sorted_features = sorted(features_with_ms2.items(), key=lambda x: x[1].get('mz', 0))
    feature_list = [fid for fid, _ in sorted_features[:num_nodes*2]]  # Get more candidates
    
    # Compute similarity matrix for candidates
    n = len(feature_list)
    print(f"  Computing MS2 cosine similarities for {n} candidate features...")
    similarity_matrix = np.zeros((n, n))
    
    for i in range(n):
        if i % 10 == 0:
            print(f"    Processing feature {i}/{n}...")
        for j in range(i + 1, n):
            fid1 = feature_list[i]
            fid2 = feature_list[j]
            sim = cosine_similarity_ms2(ms2_spectra[fid1], ms2_spectra[fid2])
            similarity_matrix[i, j] = sim
            similarity_matrix[j, i] = sim
    
    # Create network
    G = nx.Graph()
    
    # Add edges above threshold
    edge_count = 0
    for i in range(n):
        for j in range(i + 1, n):
            if similarity_matrix[i, j] >= similarity_threshold:
                G.add_edge(feature_list[i], feature_list[j], 
                          weight=similarity_matrix[i, j],
                          cosine=similarity_matrix[i, j])
                edge_count += 1
    
    print(f"  Created network with {G.number_of_nodes()} nodes, {edge_count} edges")
    
    # Select nodes: mix of connected nodes and singletons
    # Strategy: Take top connected nodes (clusters) + some singletons (isolated nodes)
    connected_nodes = [node for node in G.nodes() if G.degree(node) > 0]
    # Singletons are features not in network at all, or in network but with degree 0
    network_node_set = set(G.nodes())
    singleton_candidates = [fid for fid in feature_list 
                           if fid not in network_node_set or G.degree(fid) == 0]
    
    # Sort connected nodes by degree (most connected first)
    connected_nodes_sorted = sorted(connected_nodes, key=lambda x: G.degree(x), reverse=True)
    
    # Select mix: ~70% connected nodes, ~30% singletons
    num_connected = int(num_nodes * 0.7)
    num_singletons = num_nodes - num_connected
    
    selected_connected = connected_nodes_sorted[:num_connected]
    selected_singletons = singleton_candidates[:num_singletons]
    
    # Add singletons to graph (as isolated nodes)
    for fid in selected_singletons:
        if fid not in G.nodes():
            G.add_node(fid)
    
    # Create subgraph with selected nodes
    selected_nodes = selected_connected + selected_singletons
    if len(selected_nodes) > num_nodes:
        # Trim if needed (prefer connected over singletons if over limit)
        selected_nodes = selected_connected[:num_connected] + selected_singletons[:num_nodes-num_connected]
    
    G = G.subgraph(selected_nodes).copy()
    
    # Count stats
    connected_count = sum(1 for node in G.nodes() if G.degree(node) > 0)
    singleton_count = G.number_of_nodes() - connected_count
    
    print(f"  Selected {G.number_of_nodes()} nodes (max m/z {max_mz}):")
    print(f"    - {connected_count} connected nodes (in networks)")
    print(f"    - {singleton_count} singleton nodes (isolated)")
    print(f"    - {G.number_of_edges()} edges")
    
    return G, features_with_ms2


def export_mgf(features, ms2_spectra, network, output_path):
    """
    Export MGF file with FEATURE_ID matching network node names.
    """
    print(f"\nExporting MGF file to {output_path}...")
    
    with open(output_path, 'w') as f:
        for node_id in network.nodes():
            if node_id in ms2_spectra:
                feat = features[node_id]
                spectrum = ms2_spectra[node_id]
                
                # Write MGF entry
                f.write("BEGIN IONS\n")
                f.write(f"FEATURE_ID={node_id}\n")
                f.write(f"PEPMASS={feat['mz']}\n")
                if feat.get('rt'):
                    f.write(f"RTINSECONDS={feat['rt'] * 60}\n")
                
                # Write MS2 peaks
                for mz, intensity in spectrum:
                    f.write(f"{mz:.6f} {intensity:.6f}\n")
                
                f.write("END IONS\n\n")
    
    print(f"  Exported {len([n for n in network.nodes() if n in ms2_spectra])} spectra to MGF")


def escape_xml(text):
    """Escape XML special characters."""
    if text is None:
        return ""
    text = str(text)
    text = text.replace('&', '&amp;')
    text = text.replace('<', '&lt;')
    text = text.replace('>', '&gt;')
    text = text.replace('"', '&quot;')
    text = text.replace("'", '&apos;')
    return text

def export_cytoscape_network(network, features, ms2_spectra, output_path):
    """
    Export network to XGMML format for Cytoscape.
    Includes MS2 data directly in node attributes (ms2mzvalues, ms2intensities).
    """
    print(f"\nExporting network to {output_path}...")
    
    # Create mapping from node_id to numeric ID for XGMML compatibility
    node_id_to_num = {}
    num_to_node_id = {}
    for idx, node_id in enumerate(network.nodes(), start=1):
        node_id_to_num[node_id] = idx
        num_to_node_id[idx] = node_id
    
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n')
        f.write('<graph label="MS2 Molecular Network" xmlns="http://www.cs.rpi.edu/XGMML" directed="0">\n')
        
        # Write nodes - use numeric IDs for XGMML compatibility
        for node_id in network.nodes():
            feat = features.get(node_id, {})
            node_num = node_id_to_num[node_id]
            node_id_escaped = escape_xml(node_id)
            
            f.write(f'  <node id="{node_num}" label="{node_id_escaped}">\n')
            f.write(f'    <att name="name" type="string" value="{node_id_escaped}"/>\n')
            f.write(f'    <att name="mz" type="real" value="{feat.get("mz", 0):.6f}"/>\n')
            f.write(f'    <att name="rt" type="real" value="{feat.get("rt", 0):.6f}"/>\n')
            f.write(f'    <att name="intensity" type="real" value="{feat.get("intensity", 0):.6f}"/>\n')
            file_val = escape_xml(feat.get('file', ''))
            f.write(f'    <att name="file" type="string" value="{file_val}"/>\n')
            
            # Add MS2 data directly to node attributes (comma-separated strings)
            if node_id in ms2_spectra:
                spectrum = ms2_spectra[node_id]
                mz_values = ','.join([f"{mz:.6f}" for mz, _ in spectrum])
                intensity_values = ','.join([f"{intensity:.6f}" for _, intensity in spectrum])
                f.write(f'    <att name="ms2mzvalues" type="string" value="{escape_xml(mz_values)}"/>\n')
                f.write(f'    <att name="ms2intensities" type="string" value="{escape_xml(intensity_values)}"/>\n')
            
            f.write('  </node>\n')
        
        # Write edges - use numeric IDs for source/target
        for u, v, data in network.edges(data=True):
            u_num = node_id_to_num[u]
            v_num = node_id_to_num[v]
            f.write(f'  <edge source="{u_num}" target="{v_num}">\n')
            f.write(f'    <att name="cosine" type="real" value="{data.get("cosine", 0):.4f}"/>\n')
            f.write(f'    <att name="weight" type="real" value="{data.get("weight", 0):.4f}"/>\n')
            f.write('  </edge>\n')
        
        f.write('</graph>\n')
    
    print(f"  Exported network with {network.number_of_nodes()} nodes, {network.number_of_edges()} edges")


def export_cytoscape_session(network, features, ms2_spectra, output_path):
    """
    Export network to Cytoscape .cys session file format.
    Includes MS2 data directly in node attributes (ms2mzvalues, ms2intensities).
    A .cys file is a ZIP archive containing XML files.
    """
    print(f"\nExporting Cytoscape session to {output_path}...")
    
    # Create a temporary directory structure
    with tempfile.TemporaryDirectory() as temp_dir:
        temp_path = Path(temp_dir)
        
        # Create cysession.xml root element
        # Cytoscape session format uses CySession as root with namespace
        session_xml = ET.Element('CySession')
        session_xml.set('xmlns', 'http://www.cytoscape.org')
        session_xml.set('appName', 'Cytoscape')
        session_xml.set('version', '3.10.4')
        
        # Create networks element
        networks_elem = ET.SubElement(session_xml, 'networks')
        network_elem = ET.SubElement(networks_elem, 'network')
        network_elem.set('name', 'MS2 Molecular Network')
        network_elem.set('id', '1')
        network_elem.set('file', 'networks/network1.xgmml')
        
        # Create network XGMML content
        network_xml = ET.Element('graph')
        network_xml.set('label', 'MS2 Molecular Network')
        network_xml.set('xmlns', 'http://www.cs.rpi.edu/XGMML')
        network_xml.set('directed', '0')
        
        # Create mapping from node_id to numeric ID for XGMML compatibility
        node_id_to_num = {}
        for idx, node_id in enumerate(network.nodes(), start=1):
            node_id_to_num[node_id] = idx
        
        # Add nodes with attributes
        for node_id in network.nodes():
            feat = features.get(node_id, {})
            node_num = node_id_to_num[node_id]
            node_elem = ET.SubElement(network_xml, 'node')
            node_elem.set('id', str(node_num))
            node_elem.set('label', str(node_id))
            
            # Add attributes
            attrs = [
                ('name', 'string', str(node_id)),
                ('mz', 'real', f"{feat.get('mz', 0):.6f}"),
                ('rt', 'real', f"{feat.get('rt', 0):.6f}"),
                ('intensity', 'real', f"{feat.get('intensity', 0):.6f}"),
                ('file', 'string', feat.get('file', '')),
            ]
            
            # Add MS2 data if available
            if node_id in ms2_spectra:
                spectrum = ms2_spectra[node_id]
                mz_values = ','.join([f"{mz:.6f}" for mz, _ in spectrum])
                intensity_values = ','.join([f"{intensity:.6f}" for _, intensity in spectrum])
                attrs.extend([
                    ('ms2mzvalues', 'string', mz_values),
                    ('ms2intensities', 'string', intensity_values),
                ])
            
            for name, attr_type, value in attrs:
                att_elem = ET.SubElement(node_elem, 'att')
                att_elem.set('name', name)
                att_elem.set('type', attr_type)
                att_elem.set('value', str(value))
        
        # Add edges - use numeric IDs
        for u, v, data in network.edges(data=True):
            edge_elem = ET.SubElement(network_xml, 'edge')
            edge_elem.set('source', str(node_id_to_num[u]))
            edge_elem.set('target', str(node_id_to_num[v]))
            # Note: XGMML edges are undirected by default, skip directed attribute
            
            # Add edge attributes
            cosine = data.get('cosine', 0)
            weight = data.get('weight', 0)
            
            for name, attr_type, value in [
                ('cosine', 'real', f"{cosine:.4f}"),
                ('weight', 'real', f"{weight:.4f}"),
            ]:
                att_elem = ET.SubElement(edge_elem, 'att')
                att_elem.set('name', name)
                att_elem.set('type', attr_type)
                att_elem.set('value', str(value))
        
        # Write network XGMML to file
        networks_dir = temp_path / 'networks'
        networks_dir.mkdir()
        network_file = networks_dir / 'network1.xgmml'
        
        # Format XML nicely
        rough_string = ET.tostring(network_xml, encoding='unicode')
        reparsed = minidom.parseString(rough_string)
        pretty_xml = reparsed.toprettyxml(indent="  ")
        
        with open(network_file, 'w', encoding='utf-8') as f:
            f.write('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n')
            # Skip the XML declaration from minidom as we already wrote it
            lines = pretty_xml.split('\n')
            if lines[0].startswith('<?xml'):
                f.write('\n'.join(lines[1:]))
            else:
                f.write(pretty_xml)
        
        # Write cysession.xml (Cytoscape expects this name)
        session_file = temp_path / 'cysession.xml'
        rough_string = ET.tostring(session_xml, encoding='unicode')
        reparsed = minidom.parseString(rough_string)
        pretty_xml = reparsed.toprettyxml(indent="  ")
        
        with open(session_file, 'w', encoding='utf-8') as f:
            f.write(pretty_xml)
        
        # Create ZIP file (.cys)
        with zipfile.ZipFile(output_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
            zipf.write(session_file, 'cysession.xml')
            zipf.write(network_file, 'networks/network1.xgmml')
        
        # Create a simple properties file (optional but often included)
        props_file = temp_path / 'props.props'
        with open(props_file, 'w') as f:
            f.write(f"sessionTimestamp={int(datetime.now().timestamp() * 1000)}\n")
        
        # Add props file to ZIP
        with zipfile.ZipFile(output_path, 'a', zipfile.ZIP_DEFLATED) as zipf:
            zipf.write(props_file, 'props.props')
    
    print(f"  Exported .cys session with {network.number_of_nodes()} nodes, {network.number_of_edges()} edges")
    print(f"  MS2 data included in node attributes (ms2mzvalues, ms2intensities)")
    print(f"  Note: If the .cys file doesn't open, try importing the .xgmml file instead")
    print(f"        and saving it as .cys from within Cytoscape for full compatibility.")


def main():
    parser = argparse.ArgumentParser(
        description='Create test network and MGF from mzML files for msplot plugin',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Example:
  python3 create_test_network.py sample_mzml_lq --num-nodes 50 --max-mz 500
  
Output:
  - test_network.mgf: MGF file with FEATURE_ID matching node names
  - test_network.xgmml: Cytoscape network file
        """
    )
    parser.add_argument('input_dir', help='Directory containing mzML files')
    parser.add_argument('--output-mgf', default='test_network.mgf', 
                       help='Output MGF file (default: test_network.mgf)')
    parser.add_argument('--output-network', default='test_network.xgmml', 
                       help='Output network file (default: test_network.xgmml). Use .cys extension for Cytoscape session file.')
    parser.add_argument('--num-nodes', type=int, default=50, 
                       help='Number of nodes in network (default: 50, range: 10-100)')
    parser.add_argument('--max-mz', type=float, default=500.0,
                       help='Maximum m/z value for node selection (default: 500.0)')
    parser.add_argument('--blank-file', help='Blank mzML file (auto-detected if contains "blank")')
    parser.add_argument('--similarity-threshold', type=float, default=0.7,
                       help='Minimum cosine similarity for edges (default: 0.7)')
    parser.add_argument('--intensity-ratio', type=float, default=3.0,
                       help='Intensity ratio threshold for blank subtraction (default: 3.0)')
    
    args = parser.parse_args()
    
    # Validate num_nodes
    if args.num_nodes < 10 or args.num_nodes > 100:
        print(f"Warning: num-nodes should be 10-100, got {args.num_nodes}. Adjusting...")
        args.num_nodes = max(10, min(100, args.num_nodes))
    
    input_dir = Path(args.input_dir)
    if not input_dir.exists():
        print(f"Error: Directory {input_dir} does not exist")
        sys.exit(1)
    
    # Find mzML files
    mzml_files = sorted(input_dir.glob('*.mzML'))
    if not mzml_files:
        print(f"Error: No mzML files found in {input_dir}")
        sys.exit(1)
    
    print(f"Found {len(mzml_files)} mzML files")
    
    # Identify blank file
    blank_file = None
    if args.blank_file:
        blank_file = Path(args.blank_file)
        if not blank_file.exists():
            print(f"Error: Blank file {blank_file} does not exist")
            sys.exit(1)
    else:
        # Auto-detect blank
        for f in mzml_files:
            if 'blank' in f.name.lower():
                blank_file = f
                break
    
    if blank_file:
        print(f"Using blank file: {blank_file.name}")
        sample_files = [f for f in mzml_files if f != blank_file]
    else:
        print("Warning: No blank file detected, proceeding without blank subtraction")
        sample_files = list(mzml_files)
        blank_file = None
    
    print(f"Processing {len(sample_files)} sample files\n")
    
    # Step 1: Extract features from blank
    blank_features = {}
    blank_ms2 = {}
    if blank_file:
        blank_features, blank_ms2 = extract_features_from_mzml(blank_file, is_blank=True)
    
    # Step 2: Extract features from samples
    all_features = {}
    all_ms2 = {}
    
    for mzml_file in sample_files:
        features, ms2 = extract_features_from_mzml(mzml_file, is_blank=False)
        all_features.update(features)
        all_ms2.update(ms2)
    
    # Step 3: Blank subtraction
    if blank_file and blank_features:
        all_features = blank_subtraction(all_features, blank_features, args.intensity_ratio)
        # Also remove MS2 for removed features
        all_ms2 = {fid: spec for fid, spec in all_ms2.items() if fid in all_features}
    
    # Step 4: Create network
    network, network_features = create_network(
        all_features, all_ms2, 
        num_nodes=args.num_nodes,
        similarity_threshold=args.similarity_threshold,
        max_mz=args.max_mz
    )
    
    if network.number_of_nodes() == 0:
        print("Error: No network created. Try lowering --similarity-threshold")
        sys.exit(1)
    
    # Step 5: Export MGF file
    output_mgf = Path(args.output_mgf)
    export_mgf(network_features, all_ms2, network, output_mgf)
    
    # Step 6: Export network file
    output_network = Path(args.output_network)
    
    # Determine if we should create .cys file (auto-detect from extension)
    output_is_cys = output_network.suffix.lower() == '.cys'
    
    if output_is_cys:
        # Export as .cys session file (includes MS2 data)
        export_cytoscape_session(network, network_features, all_ms2, output_network)
        # Also create XGMML version
        xgmml_output = output_network.with_suffix('.xgmml')
        export_cytoscape_network(network, network_features, all_ms2, xgmml_output)
        print(f"\n✓ Success! Created:")
        print(f"  - MGF file: {output_mgf}")
        print(f"  - Cytoscape session (.cys): {output_network}")
        print(f"  - Network file (XGMML): {xgmml_output}")
        print(f"\nTo use in Cytoscape:")
        print(f"  1. Try opening {output_network} in Cytoscape (File → Open → File)")
        print(f"     If that doesn't work, import {xgmml_output} instead (File → Import → Network → File)")
        print(f"     and save it as .cys from within Cytoscape for full compatibility.")
        print(f"  2. The network already includes ms2mzvalues and ms2intensities columns!")
        print(f"  3. Select nodes/edges to view MS2 spectra!")
    else:
        # Export as XGMML (includes MS2 data)
        export_cytoscape_network(network, network_features, all_ms2, output_network)
        print(f"\n✓ Success! Created:")
        print(f"  - MGF file: {output_mgf}")
        print(f"  - Network file (XGMML): {output_network}")
        print(f"\nTo use in Cytoscape:")
        print(f"  1. Open {output_network} in Cytoscape (File → Import → Network → File)")
        print(f"  2. The network already includes ms2mzvalues and ms2intensities columns!")
        print(f"  3. (Optional) Load {output_mgf} if you want to reload/update MS2 data")
        print(f"  4. Select nodes/edges to view MS2 spectra!")
        print(f"\nTip: Use --output-network test_network.cys to create a .cys session file instead!")


if __name__ == '__main__':
    main()
