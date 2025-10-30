#
# Licensed to Elasticsearch B.V. under one or more contributor
# license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright
# ownership. Elasticsearch B.V. licenses this file to you under
# the Apache License, Version 2.0 (the "License"); you may
# not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

#!/bin/bash
# WIP, need to remove original md files and flatten the structure, then cleanup. also fix mv error
# Unzipping and extracting only esql docs
unzip -d md-docs-new docs-new
mkdir coverted-docs
mv md-docs-new/reference/query-languages/esql/commands/* coverted-docs
mv md-docs-new/reference/query-languages/esql/functions-operators coverted-docs
rm docs-new
rm -rf md-docs-new

# Need to extract single functions from files so that we have one file per function
output_dir="coverted-docs"

# Using awk to detect when one function starts and prints it to a new file (thanks Copilot)
# File name will be lowercased to be coherent with the others
find coverted-docs/functions-operators -maxdepth 1 -name "*.md" -type f | while read -r mdfile; do
  awk -v outdir="$output_dir" '
    # When we see a line like: ## `WORD`
    /^## `[A-Za-z0-9_-]+`/ {
      if (section) {
        close(outfile)
      }
      section=1
      # Extract WORD from the line using sub and split
      heading=$0
      sub(/^## `/,"",heading)
      sub(/`$/,"",heading)
      word=heading
      outfile=outdir "/" tolower(word) ".md"
      print $0 > outfile
      next
    }
    section {
      print $0 > outfile
    }
  ' "$mdfile"
done

# Cleanup old functions
rm -rf coverted-docs/functions-operators

# Using pandoc to convert md files into html files
# Also while iterating, creating a txt file with all the names
touch ./coverted-docs/docs-names.txt
find . -name "*.md" -type f |
while read -r mdfile;
do htmlfile="${mdfile%.md}.html";
  pandoc -f markdown "$mdfile" -o "$htmlfile";
  printf "$htmlfile\n" >> coverted-docs/docs-names.txt
  rm "$mdfile"
done

