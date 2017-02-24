#!/usr/bin/env bash

npm install
node_modules/remark/bin/remark -f *.md **/*.md java-style-guide/**/*.md best-practices/**/*.md
