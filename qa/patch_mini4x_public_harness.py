#!/usr/bin/env python3
from pathlib import Path
import sys

p=Path(sys.argv[1] if len(sys.argv)>1 else 'qa/mini4x_emulator_flow.sh')
s=p.read_text()
assert 'tap_expect TITLE 540 1416' in s
assert 'tap_expect SETUP 540 1416' not in s

old_device='''if [ "$boot" != "1" ]; then mark "FAIL device_not_booted value=$boot"; exit 1; fi
adb20 shell wm size 1080x2400'''
new_device='''if [ "$boot" != "1" ]; then mark "FAIL device_not_booted value=$boot"; exit 1; fi
adb20 shell settings put global hide_error_dialogs 1
dialog_guard="$(adb20 shell settings get global hide_error_dialogs | tr -d '\\r')"
if [ "$dialog_guard" != "1" ]; then mark "FAIL system_dialog_guard value=$dialog_guard"; exit 1; fi
adb20 shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
sleep .5
mark "SYSTEM_DIALOG_GUARD_OK hide_error_dialogs=$dialog_guard"
adb20 shell wm size 1080x2400'''
assert s.count(old_device)==1,'system-dialog guard anchor drifted'
s=s.replace(old_device,new_device,1)

readiness=r'''wait_input_ready(){
  local expected="${1:-}" attempt before count line=""
  for attempt in {1..12}; do
    before=$(qa_count)
    timeout 10s adb shell input tap 12 12 >/dev/null 2>&1 || true
    for _ in {1..12}; do
      count=$(qa_count);line=$(qa_latest)
      if [ "$count" -gt "$before" ] && printf '%s' "$line" | grep -Eq 'screen=[A-Z_]+' && ! printf '%s' "$line" | grep -q 'scenario='; then
        if [ -z "$expected" ] || printf '%s' "$line" | grep -Eq "screen=${expected}( |$)"; then
          mark "INPUT_READY expected=${expected:-ANY} attempt=$attempt :: $line"
          sleep .45
          return 0
        fi
      fi
      sleep .25
    done
    sleep .35
  done
  mark "FAIL input_not_ready expected=${expected:-ANY} latest=$line"
  return 1
}
'''
assert s.count('\nstart() {\n')==1,'start anchor drifted'
s=s.replace('\nstart() {\n','\n'+readiness+'start() {\n',1)

old_local='  local args=("$@") scenario="" before count line\n'
new_local='  local args=("$@") scenario="" before count line scenario_screen launch_out\n'
assert s.count(old_local)==1,'start locals anchor drifted'
s=s.replace(old_local,new_local,1)

old_launch='''  if ! timeout 15s adb shell am start -n "$PKG/$ACT" "$@" >> "$PROGRESS" 2>&1; then
    mark "FAIL am_start $*"
    return 1
  fi'''
new_launch='''  if ! launch_out="$(timeout 90s adb shell am start -W -n "$PKG/$ACT" "$@" 2>&1)"; then
    printf '%s\\n' "$launch_out" >> "$PROGRESS"
    mark "FAIL am_start_wait $*"
    return 1
  fi
  printf '%s\\n' "$launch_out" >> "$PROGRESS"
  if ! printf '%s\\n' "$launch_out" | grep -q '^Status: ok'; then
    mark "FAIL activity_launch_status $*"
    return 1
  fi
  mark "ACTIVITY_LAUNCH_OK $*"'''
assert s.count(old_launch)==1,'activity launch anchor drifted'
s=s.replace(old_launch,new_launch,1)

old_ready='''        mark "SCENARIO_READY $scenario :: $line"
        sleep .45
        return 0'''
new_ready='''        mark "SCENARIO_READY $scenario :: $line"
        scenario_screen=$(printf '%s\\n' "$line" | sed -nE 's/.*screen=([A-Z_]+).*/\\1/p')
        if [ -z "$scenario_screen" ]; then mark "FAIL scenario_screen_missing=$scenario latest=$line"; return 1; fi
        wait_input_ready "$scenario_screen"
        return 0'''
assert s.count(old_ready)==1,'scenario readiness anchor drifted'
s=s.replace(old_ready,new_ready,1)

old_normal='''  sleep 1.25
}
shot() {'''
new_normal='''  wait_input_ready TITLE
}
shot() {'''
assert s.count(old_normal)==1,'normal launch readiness anchor drifted'
s=s.replace(old_normal,new_normal,1)

assert 'settings put global hide_error_dialogs 1' in s
assert 'android.intent.action.CLOSE_SYSTEM_DIALOGS' in s
assert 'wait_input_ready(){' in s
assert 'adb shell input tap 12 12' in s
assert 'am start -W -n "$PKG/$ACT"' in s
assert 'timeout 90s adb shell am start -W' in s
assert 'ACTIVITY_LAUNCH_OK' in s
assert 'wait_input_ready "$scenario_screen"' in s
assert 'wait_input_ready TITLE' in s
assert 'tap_expect TITLE 540 1416' in s
assert 'tap_expect SETUP 540 1416' not in s
p.write_text(s)
print('public_harness_dialog_guard=PASS')
print('public_harness_activity_launch_wait=PASS')
print('public_harness_input_readiness=PASS')
print('public_harness_locked_title_contract=PASS')
