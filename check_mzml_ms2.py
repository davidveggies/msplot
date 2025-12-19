#!/usr/bin/env python3
"""
Quick diagnostic script to check if mzML files contain MS2 data.
"""
import sys
from pathlib import Path

try:
    import pymzml
except ImportError:
    print("Error: pymzml not installed. Install with: pip install pymzml")
    sys.exit(1)

def check_ms2_in_file(mzml_path):
    """Check if file has MS2 spectra and how to access precursor info."""
    print(f"\n=== Checking {mzml_path.name} ===")
    
    try:
        run = pymzml.run.Reader(str(mzml_path))
        ms1_count = 0
        ms2_count = 0
        ms2_with_precursor = 0
        ms2_precursor_examples = []
        
        for i, spectrum in enumerate(run):
            ms_level = spectrum.get('ms level', 0)
            
            if ms_level == 1:
                ms1_count += 1
            elif ms_level == 2:
                ms2_count += 1
                
                # Try to extract precursor info
                precursor_mz = None
                precursor_info = {}
                
                # Method 1: Check precursors attribute
                if hasattr(spectrum, 'precursors'):
                    precursor_info['has_precursors_attr'] = True
                    try:
                        prec_list = spectrum.precursors
                        if prec_list:
                            precursor_info['precursors_len'] = len(prec_list)
                            precursor_info['precursors_type'] = type(prec_list[0]).__name__ if prec_list else None
                            if prec_list and len(prec_list) > 0:
                                p = prec_list[0]
                                if isinstance(p, dict):
                                    precursor_mz = p.get('mz', None)
                                    precursor_info['precursor_mz_from_dict'] = precursor_mz
                                elif hasattr(p, 'mz'):
                                    precursor_mz = p.mz
                                    precursor_info['precursor_mz_from_attr'] = precursor_mz
                    except Exception as e:
                        precursor_info['precursors_error'] = str(e)
                else:
                    precursor_info['has_precursors_attr'] = False
                
                # Method 2: Check selected_precursors
                if precursor_mz is None and hasattr(spectrum, 'selected_precursors'):
                    try:
                        selected = spectrum.selected_precursors
                        if selected:
                            precursor_info['has_selected_precursors'] = True
                            if isinstance(selected[0], dict):
                                precursor_mz = selected[0].get('mz', None)
                            elif hasattr(selected[0], 'mz'):
                                precursor_mz = selected[0].mz
                    except Exception as e:
                        pass
                
                # Method 3: Dictionary access
                if precursor_mz is None:
                    for key in ['selected ion m/z', 'base peak m/z', 'precursor m/z']:
                        val = spectrum.get(key, None)
                        if val:
                            try:
                                precursor_mz = float(val)
                                precursor_info[f'precursor_from_{key}'] = precursor_mz
                                break
                            except (ValueError, TypeError):
                                pass
                
                if precursor_mz:
                    ms2_with_precursor += 1
                    if len(ms2_precursor_examples) < 3:
                        ms2_precursor_examples.append({
                            'spectrum_idx': i,
                            'precursor_mz': precursor_mz,
                            'method': list(precursor_info.keys())[-1] if precursor_info else 'unknown',
                            'peaks_count': len(spectrum.peaks('raw')) if spectrum.peaks('raw') else 0
                        })
                
                # Print details for first few MS2 spectra
                if ms2_count <= 3:
                    print(f"  MS2 spectrum #{ms2_count}:")
                    print(f"    Index: {i}")
                    print(f"    Precursor info: {precursor_info}")
                    if precursor_mz:
                        print(f"    ✓ Precursor m/z: {precursor_mz:.4f}")
                        peaks = spectrum.peaks('raw')
                        if peaks:
                            print(f"    Peaks: {len(peaks)}")
                    else:
                        print(f"    ✗ No precursor m/z found")
                    
                    # Show all available keys
                    print(f"    Available keys: {list(spectrum.keys())[:10]}...")
        
        print(f"\nSummary:")
        print(f"  MS1 spectra: {ms1_count}")
        print(f"  MS2 spectra: {ms2_count}")
        print(f"  MS2 with precursor: {ms2_with_precursor}")
        
        if ms2_precursor_examples:
            print(f"\nExample MS2 precursors found:")
            for ex in ms2_precursor_examples:
                print(f"  Spectrum {ex['spectrum_idx']}: m/z={ex['precursor_mz']:.4f} "
                      f"(via {ex['method']}), {ex['peaks_count']} peaks")
        
        return ms2_count > 0, ms2_with_precursor > 0
        
    except Exception as e:
        print(f"  Error: {e}")
        import traceback
        traceback.print_exc()
        return False, False

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python3 check_mzml_ms2.py <mzml_file_or_dir>")
        sys.exit(1)
    
    input_path = Path(sys.argv[1])
    
    if input_path.is_file():
        check_ms2_in_file(input_path)
    elif input_path.is_dir():
        mzml_files = list(input_path.glob("*.mzML")) + list(input_path.glob("*.mzml"))
        for mzml_file in sorted(mzml_files):
            check_ms2_in_file(mzml_file)
    else:
        print(f"Error: {input_path} is not a file or directory")
        sys.exit(1)

