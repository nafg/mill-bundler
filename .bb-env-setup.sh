#!/usr/bin/env bash
# Generates .bsp/mill-bsp.json (gitignored) so IDEs can import the worktree over BSP. Non-fatal.

mill mill.bsp.BSP/install || true
