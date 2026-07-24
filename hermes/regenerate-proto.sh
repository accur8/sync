#!/usr/bin/env bash
set -euo pipefail

# Script to regenerate ScalaPB code from proto files using scalapbc CLI
# Generated Scala files are committed to git in src/main/scala/

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GODEV_DIR="/Users/glen/code/accur8/godev"
PROTO_DIR="$SCRIPT_DIR/../hermes-proto/src/main/protobuf"
SCALA_OUT_DIR="$SCRIPT_DIR/../hermes-proto/src/main/scala"

echo "=== Copying proto files from godev ==="

# Create proto directory if it doesn't exist
mkdir -p "$PROTO_DIR"

# MIRROR GODEV'S LAYOUT — do not flatten.
#
# protos import each other by REPO-ROOT-RELATIVE path (continuum_rpc.proto imports
# "pkg/discovery/discovery.proto"; wsmessages.proto imports "continuum/rpc/continuum_rpc.proto"
# and "pkg/rpc/auth/auth.proto"). Copying files to flat names breaks every one of those,
# and copying the SAME file to two paths is worse: protoc treats path as identity, so the
# messages appear twice and every cross-file reference becomes ambiguous.
#
# Keeping the tree identical to godev's means an import that resolves there resolves here,
# with no rewriting and no special cases.
copy_proto() {
  local rel="$1"
  mkdir -p "$PROTO_DIR/$(dirname "$rel")"
  cp "$GODEV_DIR/$rel" "$PROTO_DIR/$rel"
}

# Clear previously-flattened copies so a stale duplicate cannot shadow the real tree.
rm -f "$PROTO_DIR"/continuum_rpc.proto "$PROTO_DIR"/wsmessages.proto "$PROTO_DIR"/auth.proto \
      "$PROTO_DIR"/mailbox.proto "$PROTO_DIR"/process_rpc.proto "$PROTO_DIR"/db.proto \
      "$PROTO_DIR"/fileset.proto

PROTOS=(
  continuum/rpc/continuum_rpc.proto
  mesh/hproto/wsmessages.proto
  pkg/rpc/auth/auth.proto
  pkg/rpc/mailbox/mailbox.proto
  pkg/rpc/process/process_rpc.proto
  pkg/rpc/db/db.proto
  pkg/rpc/fileset/fileset.proto
  pkg/discovery/discovery.proto
)
for rel in "${PROTOS[@]}"; do copy_proto "$rel"; done

echo "✓ Copied ${#PROTOS[@]} proto files, mirroring godev's layout"

# Fix package declarations for Scala
echo ""
echo "=== Fixing proto package declarations ==="

# Walk the mirrored tree; google/protobuf/* are vendored well-known types and are skipped.
while IFS= read -r proto; do
  filename=$(basename "$proto")
  echo "Processing $filename..."

  # Add Scala-specific options if not present
  if ! grep -q "option java_package" "$proto"; then
    case "$filename" in
      continuum_rpc.proto)
        package_line='option java_package = "a8.hermes.proto.continuum";'
        ;;
      wsmessages.proto)
        package_line='option java_package = "a8.hermes.proto.process";'
        ;;
      auth.proto)
        package_line='option java_package = "a8.hermes.proto.auth";'
        ;;
      mailbox.proto)
        package_line='option java_package = "a8.hermes.proto.mailbox";'
        ;;
      process_rpc.proto)
        package_line='option java_package = "a8.hermes.proto.process";'
        ;;
      db.proto)
        package_line='option java_package = "a8.hermes.proto.db";'
        ;;
      fileset.proto)
        package_line='option java_package = "a8.hermes.proto.fileset";'
        ;;
      discovery.proto)
        package_line='option java_package = "a8.hermes.proto.discovery";'
        ;;
    esac

    # Insert after the package declaration
    sed -i '' "/^package /a\\
$package_line\\
option java_multiple_files = true;
" "$proto"
  fi
done < <(find "$PROTO_DIR" -name '*.proto' -not -path "$PROTO_DIR/google/*")

echo "✓ Fixed package declarations"

# Generate Scala code with scalapbc
echo ""
echo "=== Generating Scala code with scalapbc ==="

# Check if scalapbc is available
if ! command -v scalapbc &> /dev/null; then
  echo "ERROR: scalapbc not found in PATH"
  echo "Make sure you've run 'direnv allow' after adding protobuf fragment to flake.nix"
  exit 1
fi

echo "Using scalapbc: $(which scalapbc)"
echo "ScalaPB version: $(scalapbc --version)"

# Remove old generated code
echo "Removing old generated Scala files..."
rm -rf "$SCALA_OUT_DIR/a8/hermes/proto"
rm -rf "$SCALA_OUT_DIR/com/google/protobuf"

# Generate new code
# scalapbc options:
#   --scala_out=grpc=false:<dir>  - Output directory; grpc=false disables gRPC stub generation
#   --proto_path=<dir>            - Where to find proto files (can be repeated)
echo "Generating Scala code..."

# Generate for every mirrored proto. --proto_path is the tree root, so the repo-root-relative
# imports inside the files resolve exactly as they do in godev.
mapfile -t GEN_PROTOS < <(find "$PROTO_DIR" -name '*.proto' -not -path "$PROTO_DIR/google/*")
scalapbc \
  "--scala_out=$SCALA_OUT_DIR" \
  "--proto_path=$PROTO_DIR" \
  "${GEN_PROTOS[@]}"

echo "✓ Generated Scala code"

# Count generated files
GENERATED_COUNT=$(find "$SCALA_OUT_DIR/a8/hermes/proto" -name "*.scala" 2>/dev/null | wc -l)
echo ""
echo "=== Summary ==="
echo "Proto files: $PROTO_DIR"
echo "Generated Scala: $SCALA_OUT_DIR"
echo "Generated files: $GENERATED_COUNT Scala files"
echo ""
echo "Generated files are in standard src/main/scala/ directory."
echo "Run 'git add hermes-proto/src/main/scala' to commit them."
