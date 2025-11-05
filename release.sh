#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if version argument provided
if [ -z "$1" ]; then
    echo -e "${RED}Error: Version number required${NC}"
    echo "Usage: ./release.sh <version>"
    echo "Example: ./release.sh 1.2.23"
    exit 1
fi

NEW_VERSION="$1"
echo -e "${GREEN}Creating release v${NEW_VERSION}${NC}"

# Validate version format (semantic versioning)
if ! [[ "$NEW_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo -e "${RED}Error: Version must be in format X.Y.Z (e.g., 1.2.23)${NC}"
    exit 1
fi

# Check for uncommitted changes
if ! git diff-index --quiet HEAD --; then
    echo -e "${YELLOW}Warning: You have uncommitted changes${NC}"
    read -p "Continue anyway? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Update version in build.gradle
echo -e "${YELLOW}Updating build.gradle...${NC}"
sed -i "s/^version = '.*'$/version = '${NEW_VERSION}'/" build.gradle

# Update version in mods.toml
echo -e "${YELLOW}Updating mods.toml...${NC}"
sed -i "s/^version=\".*\"$/version=\"${NEW_VERSION}\"/" src/main/resources/META-INF/mods.toml

# Update version in ModUpdater.java welcome message
echo -e "${YELLOW}Updating ModUpdater.java...${NC}"
sed -i "s/ModUpdater v[0-9]\+\.[0-9]\+\.[0-9]\+/ModUpdater v${NEW_VERSION}/" src/main/java/com/wcholmes/modupdater/ModUpdater.java

# Show changes
echo -e "${GREEN}Version updated to ${NEW_VERSION} in:${NC}"
git diff build.gradle src/main/resources/META-INF/mods.toml src/main/java/com/wcholmes/modupdater/ModUpdater.java

# Confirm before committing
read -p "Commit and push these changes? (y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}Aborting. Changes have been made but not committed.${NC}"
    exit 1
fi

# Commit changes
echo -e "${YELLOW}Committing changes...${NC}"
git add build.gradle src/main/resources/META-INF/mods.toml src/main/java/com/wcholmes/modupdater/ModUpdater.java
git commit -m "Update to v${NEW_VERSION}

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>"

# Create and push tag
echo -e "${YELLOW}Creating tag v${NEW_VERSION}...${NC}"
git tag "v${NEW_VERSION}"

echo -e "${YELLOW}Pushing to GitHub...${NC}"
git push origin main
git push origin "v${NEW_VERSION}"

echo -e "${GREEN}✓ Release v${NEW_VERSION} created!${NC}"
echo -e "${GREEN}✓ CI/CD workflow will build and sign the JAR${NC}"
echo -e "${GREEN}✓ Watch progress: gh run watch${NC}"
echo -e "${GREEN}✓ View release: https://github.com/wcholmes42/mod-updater/releases/tag/v${NEW_VERSION}${NC}"
