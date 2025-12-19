#!/usr/bin/env python3
"""
Create a simple 4-node test network for Cytoscape:
- 2 connected nodes (with an edge)
- 2 singleton nodes (isolated)
- Fake MS2 m/z and intensity data for all nodes
"""

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

def create_simple_test_network(output_path='simple_test_network.xgmml'):
    """
    Create a simple 4-node test network:
    - Node 1 & 2: Connected (edge between them)
    - Node 3 & 4: Singletons (no edges)
    - All nodes have fake MS2 data
    """
    
    # Define fake MS2 spectra (m/z, intensity pairs)
    # Each spectrum has ~10 peaks
    fake_spectra = {
        'node1': [
            (101.0239, 5000.0), (115.0395, 8000.0), (128.0550, 12000.0),
            (142.0706, 15000.0), (156.0862, 20000.0), (170.1018, 18000.0),
            (184.1174, 10000.0), (198.1330, 6000.0), (212.1486, 3000.0), (226.1642, 1000.0)
        ],
        'node2': [
            (102.0312, 4500.0), (116.0468, 7500.0), (129.0624, 11000.0),
            (143.0780, 14000.0), (157.0936, 19000.0), (171.1092, 17000.0),
            (185.1248, 9500.0), (199.1404, 5500.0), (213.1560, 2800.0), (227.1716, 900.0)
        ],
        'node3': [
            (150.0456, 7000.0), (164.0612, 10000.0), (178.0768, 13000.0),
            (192.0924, 16000.0), (206.1080, 14000.0), (220.1236, 9000.0),
            (234.1392, 5000.0), (248.1548, 2500.0), (262.1704, 1200.0), (276.1860, 500.0)
        ],
        'node4': [
            (200.0891, 6000.0), (214.1047, 9000.0), (228.1203, 12000.0),
            (242.1359, 15000.0), (256.1515, 13000.0), (270.1671, 8000.0),
            (284.1827, 4500.0), (298.1983, 2200.0), (312.2139, 1000.0), (326.2295, 400.0)
        ]
    }
    
    # Node metadata (m/z, rt, intensity for MS1)
    node_metadata = {
        'node1': {'mz': 215.1180, 'rt': 5.2, 'intensity': 500000.0},
        'node2': {'mz': 216.1250, 'rt': 5.4, 'intensity': 480000.0},
        'node3': {'mz': 278.1650, 'rt': 8.1, 'intensity': 350000.0},
        'node4': {'mz': 328.2100, 'rt': 12.5, 'intensity': 290000.0},
    }
    
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n')
        f.write('<graph label="Simple Test Network" xmlns="http://www.cs.rpi.edu/XGMML" directed="0">\n')
        
        # Write nodes
        node_id_to_num = {'node1': 1, 'node2': 2, 'node3': 3, 'node4': 4}
        
        for node_id in ['node1', 'node2', 'node3', 'node4']:
            node_num = node_id_to_num[node_id]
            metadata = node_metadata[node_id]
            spectrum = fake_spectra[node_id]
            
            # Position nodes in a simple layout - spread them out
            # node1 at (-100, -100), node2 at (100, -100), node3 at (-100, 100), node4 at (100, 100)
            positions = {'node1': (-100.0, -100.0), 'node2': (100.0, -100.0), 
                        'node3': (-100.0, 100.0), 'node4': (100.0, 100.0)}
            x, y = positions[node_id]
            
            f.write(f'  <node id="{node_num}" label="{node_id}">\n')
            f.write(f'    <att name="name" type="string" value="{node_id}"/>\n')
            f.write(f'    <att name="mz" type="real" value="{metadata["mz"]:.6f}"/>\n')
            f.write(f'    <att name="rt" type="real" value="{metadata["rt"]:.2f}"/>\n')
            f.write(f'    <att name="intensity" type="real" value="{metadata["intensity"]:.2f}"/>\n')
            
            # Add Cytoscape-specific position attributes (Cytoscape may not use these without layout)
            # But they can help - Cytoscape will likely need a layout applied after import
            f.write(f'    <graphics type="ELLIPSE" x="{x}" y="{y}" w="35.0" h="35.0" fill="#CCCCCC" outline="#000000"/>\n')
            
            # Add MS2 data
            mz_values = ','.join([f"{mz:.6f}" for mz, _ in spectrum])
            intensity_values = ','.join([f"{intensity:.6f}" for _, intensity in spectrum])
            f.write(f'    <att name="ms2mzvalues" type="string" value="{mz_values}"/>\n')
            f.write(f'    <att name="ms2intensities" type="string" value="{intensity_values}"/>\n')
            
            f.write('  </node>\n')
        
        # Write edges (only between node1 and node2)
        f.write('  <edge source="1" target="2">\n')
        f.write('    <att name="cosine" type="real" value="0.8500"/>\n')
        f.write('    <att name="weight" type="real" value="0.8500"/>\n')
        f.write('  </edge>\n')
        
        f.write('</graph>\n')
    
    print(f"✓ Created simple test network: {output_path}")
    print(f"  - 4 nodes total")
    print(f"  - 2 connected nodes (node1 ↔ node2)")
    print(f"  - 2 singleton nodes (node3, node4)")
    print(f"  - All nodes have MS2 m/z and intensity data")
    print(f"\nTo use in Cytoscape:")
    print(f"  1. File → Import → Network → File → select {output_path}")
    print(f"  2. If all nodes appear on top of each other:")
    print(f"     → Go to Layout → Apply Preferred Layout (or Layout → yFiles Layouts → Organic)")
    print(f"     This will spread out the nodes in the visualization")

if __name__ == '__main__':
    import sys
    output_file = sys.argv[1] if len(sys.argv) > 1 else 'simple_test_network.xgmml'
    create_simple_test_network(output_file)

