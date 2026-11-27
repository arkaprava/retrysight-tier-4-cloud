#!/usr/bin/env python3
# Copyright (c) 2026 Arka Prava Ghosh
# SPDX-License-Identifier: Apache-2.0

import json
import re
import sys

text = open(sys.argv[1], encoding="utf-8").read()
match = re.search(r"\[\s*\{", text)
if not match:
    sys.exit(1)
json.loads(text[match.start():])
