#!/usr/bin/env python3
from pathlib import Path
import runpy

# Release-candidate sync point: all production gates consume this exact AI stack.
root=Path(__file__).parent
runpy.run_path(str(root/'apply_v14_ai_quality_v2.py'), run_name='__main__')
runpy.run_path(str(root/'apply_v14_ai_quality_v3.py'), run_name='__main__')
runpy.run_path(str(root/'apply_v14_ai_quality_v4.py'), run_name='__main__')
