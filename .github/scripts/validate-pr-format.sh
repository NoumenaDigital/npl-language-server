#!/bin/bash
set -e

# Get PR body from GitHub event file
PR_BODY=$(jq -r '.pull_request.body' "$GITHUB_EVENT_PATH")

# Check for placeholder comment
if [[ "$PR_BODY" == *"<!-- Description of the PR changes -->"* ]]; then
  echo "Error: PR description still contains placeholder comment. Please replace it with actual description."
  exit 1
fi

# Check Ticket format if present
if [[ "$PR_BODY" == *"Ticket: "* ]]; then
  TICKET=$(echo "$PR_BODY" | grep -o "Ticket: ST-[0-9]*" || echo "")
  if [[ -z "$TICKET" ]]; then
    echo "Error: Ticket format is invalid. Should be 'Ticket: ST-XXXX' where XXXX are numbers."
    exit 1
  fi
  
  if [[ "$PR_BODY" == *"Ticket: ST-XXXX"* ]]; then
    echo "Error: Ticket placeholder 'ST-XXXX' detected. Please use actual ticket number."
    exit 1
  fi
fi

# Check Release value
RELEASE=$(echo "$PR_BODY" | grep -o "Release: true\|Release: false" || echo "")
if [[ -z "$RELEASE" ]]; then
  echo "Error: PR description must include 'Release: true' or 'Release: false'."
  exit 1
fi

echo "PR validation passed!"
exit 0
