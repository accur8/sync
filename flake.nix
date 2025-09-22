{
  description = "sync project";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    a8-scripts.url = "github:fizzy33/a8-scripts";
  };

  outputs = { self, nixpkgs, flake-utils, a8-scripts }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};

        # Java setup
        my-java = pkgs.openjdk11_headless;
        my-sbt = pkgs.sbt.override { jre = my-java; };

      in {
        devShells.default = pkgs.mkShell {
          buildInputs = [
            a8-scripts.packages.${system}.a8-scripts
            my-java
            my-sbt
            pkgs.python3
            pkgs.gnupg
          ];
        };
      }
    );
}