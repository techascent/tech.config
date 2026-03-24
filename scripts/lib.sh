#!/usr/bin/env bash
# scripts/lib.sh — minimal UI helpers for tech.config scripts.
# SOURCE this file; do not execute it directly.
#
# Panda palette colors, output helpers: ok, info, warn, err, header.
# Set NO_COLOR=1 to suppress color output.

_ansi_fg() {
  local hex="${1#\#}"
  printf '\033[38;2;%d;%d;%dm' \
    "$((16#${hex:0:2}))" "$((16#${hex:2:2}))" "$((16#${hex:4:2}))"
}

if [ -z "$NO_COLOR" ]; then
  _RESET=$'\033[0m'
  _BOLD=$'\033[1m'
  _OK="$(_ansi_fg "#55B96D")"       # green
  _INFO="$(_ansi_fg "#6FC1FF")"     # blue
  _WARN="$(_ansi_fg "#FFB86C")"     # orange
  _ERR="$(_ansi_fg "#FF2C6D")"      # hot pink
  _HEADER="$(_ansi_fg "#B1B9F5")"   # lavender
else
  _RESET='' _BOLD='' _OK='' _INFO='' _WARN='' _ERR='' _HEADER=''
fi

ok()         { printf "  %s✓%s %s\n"  "$_OK"              "$_RESET" "$*"; }
info()       { printf "  %s·%s %s\n"  "$_INFO"            "$_RESET" "$*"; }
warn()       { printf "  %s~%s %s\n"  "$_WARN"            "$_RESET" "$*"; }
err()        { printf "  %s✗%s %s\n"  "$_ERR"             "$_RESET" "$*" >&2; }
header()     { printf "\n%s%s%s\n"    "${_BOLD}${_HEADER}" "$*"     "$_RESET"; }
spin()       { printf "  %s↻%s  %s..." "$_INFO"            "$_RESET" "$*"; }
clear_spin() { printf '\r\033[2K'; }
