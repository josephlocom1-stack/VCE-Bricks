#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)";cd "$ROOT"
PROJECT="${1:-project}"
export MINI4X_PROJECT="$PROJECT"
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT

cat buildsrc/noassets.00.b64 buildsrc/noassets.01.b64 buildsrc/noassets.02.b64 buildsrc/noassets.03.b64 buildsrc/noassets.04a.b64 buildsrc/noassets.04b.b64 buildsrc/noassets.05.b64 buildsrc/noassets.06.b64 buildsrc/noassets.07.b64 buildsrc/noassets.08a.b64 buildsrc/noassets.08b.b64 buildsrc/noassets.09.b64 | base64 -d > "$TMP/src.zip"
unzip -t "$TMP/src.zip" >/dev/null
rm -rf "$PROJECT";mkdir -p "$PROJECT";unzip -q "$TMP/src.zip" -d "$PROJECT"
cat final-generator/generator.*.part > "$TMP/generate.py"
mkdir -p "$PROJECT/app/src/main/assets/mini4x/atlases"
python "$TMP/generate.py" "$PROJECT/app/src/main/assets/mini4x/atlases" >/dev/null

python v12-patch/apply_v12.py >/dev/null
python v13-ai/apply_v13_ai.py >/dev/null
python v13-qa/apply_v13_qa.py >/dev/null
python v14-ai/apply_v14_full_roster_ai.py >/dev/null
python v14-ai/apply_v14_ai_quality.py >/dev/null
python v14-ai/apply_v14_play_ready.py >/dev/null
python v13-qa/apply_directional_facing.py >/dev/null
python v13-qa/apply_v13_ui_review_fixes.py >/dev/null
python v13-qa/apply_v13_action_review_fixes.py >/dev/null
python v14-ai/apply_v14_diplomacy_ui.py >/dev/null
python v14-ai/apply_v14_tile_actions_ui.py >/dev/null
python v14-ai/apply_v14_result_lifecycle.py >/dev/null
python v14-ai/apply_v14_command_reachability.py >/dev/null
python v14-ai/apply_v14_game_flow_lifecycle.py >/dev/null
python v14-ai/apply_v14_selection_lifecycle.py >/dev/null
python v14-ai/apply_v14_safe_new_game.py >/dev/null

echo "v14_current_reconstruction=PASS stages=16 project=$PROJECT"
