#!/bin/bash
set -e

# Get PR body from GitHub event file
PR_BODY=$(jq -r '.pull_request.body' "$GITHUB_EVENT_PATH")

# Check for placeholder comment
if [[ "$PR_BODY" == *"<!-- Description of the PR changes -->"* ]]; then
  echo "Error: PR description still contains placeholder comment. Please replace it with actual description."
  exit 1
fi

# Check Ticket format
if ! echo "$PR_BODY" | grep -q "^Ticket: ST-[0-9]*$"; then
  echo "Error: Ticket format is invalid. Should be 'Ticket: ST-XXXX' on its own line where XXXX are numbers."
  exit 1
fi

# Check Release value
if echo "$PR_BODY" | grep -q "^Release: true$" || echo "$PR_BODY" | grep -q "^Release: false$"; then
  # Valid format found
  :
else
  echo "Error: PR description must include a line with exactly 'Release: true' or 'Release: false'."
  exit 1
fi

echo "PR validation passed!"
exit 0
