#!/bin/bash
# Quick script to update existing gh-pages documentation

set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=== Quick Documentation Update ===${NC}"
echo
echo "This script updates existing documentation on gh-pages branch."
echo "It's less destructive than publish-docs.sh but still replaces all docs."
echo
echo -e "${YELLOW}Updating GitHub Pages documentation...${NC}"

# Check for uncommitted changes
if ! git diff-index --quiet HEAD --; then
    echo -e "${RED}Error: You have uncommitted changes.${NC}"
    echo "Please commit or stash your changes first."
    exit 1
fi

# Save current branch
CURRENT_BRANCH=$(git branch --show-current)

# Generate docs
./generate-docs.sh

# Save docs to temp directory
TEMP_DIR=$(mktemp -d)
cp -r target/api-docs/* "$TEMP_DIR/"

# Switch to gh-pages
git checkout gh-pages

# Update files
rm -rf !(.|..|.git|.gitignore)
cp -r "$TEMP_DIR"/* .
touch .nojekyll

# Commit and push
git add -A
if git diff --staged --quiet; then
    echo -e "${YELLOW}No changes to documentation${NC}"
else
    git commit -m "Update docs - $(date '+%Y-%m-%d %H:%M:%S')"
    git push origin gh-pages
    echo -e "${GREEN}✓ Documentation updated!${NC}"
fi

# Return to original branch
git checkout "$CURRENT_BRANCH"
rm -rf "$TEMP_DIR"

echo -e "${GREEN}Done! Docs at: https://[your-org].github.io/sync/${NC}"