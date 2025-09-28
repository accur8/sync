{
  description = "sync project";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    devshell.url = "github:numtide/devshell";
    a8-scripts.url = "github:fizzy33/a8-scripts";
    a8-scripts.inputs.nixpkgs.follows = "nixpkgs";
  };

  outputs = { self, nixpkgs, flake-utils, devshell, a8-scripts }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          overlays = [ devshell.overlays.default ];
        };

        # Java setup
        my-java = pkgs.openjdk11_headless;
        my-sbt = pkgs.sbt.override { jre = my-java; };

      in {
        devShells.default = pkgs.devshell.mkShell {
          name = "sync";

          packages = [
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