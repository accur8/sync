#!/bin/bash
# Script to generate and publish documentation to GitHub Pages

set -e  # Exit on error

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== Accur8 Sync Documentation Publisher ===${NC}"
echo

# Save current branch
CURRENT_BRANCH=$(git branch --show-current)
echo -e "${YELLOW}Current branch: ${CURRENT_BRANCH}${NC}"

# Check for uncommitted changes
if ! git diff-index --quiet HEAD --; then
    echo -e "${RED}Error: You have uncommitted changes.${NC}"
    echo "Please commit or stash your changes before publishing docs."
    exit 1
fi

# Step 1: Generate documentation
echo
echo -e "${YELLOW}Step 1: Generating documentation...${NC}"
if [ -x "./generate-docs.sh" ]; then
    ./generate-docs.sh
else
    echo -e "${RED}Error: generate-docs.sh not found or not executable${NC}"
    exit 1
fi

# Check if docs were generated
if [ ! -d "target/api-docs" ]; then
    echo -e "${RED}Error: Documentation was not generated (target/api-docs not found)${NC}"
    exit 1
fi

# Step 2: Create temporary directory for docs
echo
echo -e "${YELLOW}Step 2: Preparing documentation for publishing...${NC}"
TEMP_DIR=$(mktemp -d)
cp -r target/api-docs/* "$TEMP_DIR/"
echo -e "${GREEN}✓ Documentation copied to temporary directory${NC}"

# Step 3: Switch to gh-pages branch
echo
echo -e "${YELLOW}Step 3: Switching to gh-pages branch...${NC}"

# Check if gh-pages branch exists
if git show-ref --verify --quiet refs/heads/gh-pages; then
    echo "gh-pages branch exists, switching to it..."
    git checkout gh-pages
    
    # Pull latest changes if the branch exists on remote
    if git show-ref --verify --quiet refs/remotes/origin/gh-pages; then
        echo "Pulling latest changes from origin/gh-pages..."
        git pull origin gh-pages --rebase
    fi
else
    echo "Creating new gh-pages branch..."
    git checkout --orphan gh-pages
    # Remove all files from the new branch
    git rm -rf . 2>/dev/null || true
fi

# Step 4: Clean directory and copy new docs
echo
echo -e "${YELLOW}Step 4: Updating documentation...${NC}"

# Remove all existing files except .git
find . -mindepth 1 -maxdepth 1 -name '.git' -prune -o -exec rm -rf {} + 2>/dev/null || true

# Copy documentation from temp directory
cp -r "$TEMP_DIR"/* .

# Add .nojekyll file to prevent Jekyll processing
touch .nojekyll

# Add a simple README for the gh-pages branch
cat > README.md << 'EOF'
# Accur8 Sync Documentation

This branch contains the generated documentation for Accur8 Sync.

View the documentation at: https://[your-org].github.io/sync/

To update the documentation:
1. Switch to the main branch
2. Run `./publish-docs.sh`

The documentation includes:
- API Reference (ScalaDoc)
- Usage Guides
- Code Examples
- Database Setup Guide
- Logging Best Practices

Generated on: $(date)
EOF

echo -e "${GREEN}✓ Documentation files copied${NC}"

# Step 5: Commit and push
echo
echo -e "${YELLOW}Step 5: Committing documentation...${NC}"

git add -A
git commit -m "Update documentation - $(date '+%Y-%m-%d %H:%M:%S')" || {
    echo -e "${YELLOW}No changes to commit${NC}"
    CHANGES=false
}

if [ "${CHANGES}" != "false" ]; then
    echo
    echo -e "${YELLOW}Step 6: Pushing to GitHub...${NC}"
    git push origin gh-pages || {
        echo -e "${YELLOW}Note: If this is your first push to gh-pages, you may need to run:${NC}"
        echo -e "${BLUE}git push --set-upstream origin gh-pages${NC}"
    }
    echo -e "${GREEN}✓ Documentation published successfully!${NC}"
else
    echo -e "${GREEN}✓ Documentation is already up to date${NC}"
fi

# Step 7: Switch back to original branch
echo
echo -e "${YELLOW}Step 7: Switching back to ${CURRENT_BRANCH}...${NC}"
git checkout "$CURRENT_BRANCH"

# Clean up
rm -rf "$TEMP_DIR"

echo
echo -e "${GREEN}Documentation publishing complete!${NC}"
echo
echo -e "${BLUE}Your documentation will be available at:${NC}"
echo -e "${BLUE}https://[your-org].github.io/sync/${NC}"
echo
echo -e "${YELLOW}Note: It may take a few minutes for GitHub Pages to update.${NC}"
echo
echo "To enable GitHub Pages if not already enabled:"
echo "1. Go to https://github.com/[your-org]/sync/settings/pages"
echo "2. Under 'Source', select 'Deploy from a branch'"
echo "3. Choose 'gh-pages' branch and '/ (root)' folder"
echo "4. Click 'Save'"