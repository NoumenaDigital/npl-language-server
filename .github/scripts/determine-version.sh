#!/bin/bash
set -e

# Extract current platform version from pom.xml
CURRENT_PLATFORM_VERSION=$(grep -m 1 "<platform.version>" pom.xml | sed -E 's/.*<platform.version>(.*)<\/platform.version>.*/\1/')
echo "Current platform version: $CURRENT_PLATFORM_VERSION"

# Check if this commit changed the platform version
git fetch origin
PREVIOUS_COMMIT=$(git rev-parse HEAD~1)
PREVIOUS_PLATFORM_VERSION=$(git show $PREVIOUS_COMMIT:pom.xml | grep -m 1 "<platform.version>" | sed -E 's/.*<platform.version>(.*)<\/platform.version>.*/\1/')
echo "Previous platform version: $PREVIOUS_PLATFORM_VERSION"

# Compare platform versions
if [ "$CURRENT_PLATFORM_VERSION" != "$PREVIOUS_PLATFORM_VERSION" ]; then
  # If platform version changed, use the new platform version as the tag
  RELEASE_TAG="$CURRENT_PLATFORM_VERSION"
else
  # Find latest release with the current platform version prefix
  LATEST_RELEASE=$(curl -s -H "Authorization: Bearer $GITHUB_TOKEN" \
    "https://api.github.com/repos/$GITHUB_REPOSITORY/releases" | \
    jq -r "[.[] | select(.tag_name | startswith(\"$CURRENT_PLATFORM_VERSION\")) | .tag_name] | sort_by(split(\"-\")[1] | if . == null then 0 else tonumber end) | last // \"\"")
  
  if [ -z "$LATEST_RELEASE" ] || [ "$LATEST_RELEASE" = "null" ]; then
    # No previous release with this platform version
    RELEASE_TAG="$CURRENT_PLATFORM_VERSION-1"
  else
    if [ "$LATEST_RELEASE" = "$CURRENT_PLATFORM_VERSION" ]; then
      # Previous release was just the plain platform version, start numbering
      RELEASE_TAG="$CURRENT_PLATFORM_VERSION-1"
    else
      # Increment the suffix number
      SUFFIX=$(echo $LATEST_RELEASE | sed -E 's/.*-([0-9]+)$/\1/')
      NEW_SUFFIX=$((SUFFIX + 1))
      RELEASE_TAG="$CURRENT_PLATFORM_VERSION-$NEW_SUFFIX"
    fi
  fi
fi

RELEASE_NAME="NPL Language Server for Platform version $RELEASE_TAG"

# Output for use in the calling script
echo "release_tag=$RELEASE_TAG"
echo "release_name=$RELEASE_NAME"

# Export variables for the parent shell to use
export release_tag=$RELEASE_TAG
export release_name=$RELEASE_NAME 
