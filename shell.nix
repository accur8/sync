{ system ? builtins.currentSystem }:
let
  sources = import ./nix/sources.nix;

  nixpkgs = import sources.nixpkgs { inherit system; };

  devshell = import sources.devshell {
    inherit nixpkgs;
    inputs = null;
    system = null;
  };

  a8-scripts = import sources.a8-scripts { inherit system nixpkgs; };

  my-java = nixpkgs.openjdk11_headless;

  my-scala = nixpkgs.scala.override { jre = my-java; };
  my-ammonite = nixpkgs.ammonite_2_13.override { jre = my-java; };

  my-sbt = nixpkgs.sbt.override { jre = my-java; };
in
devshell.mkShell ({ extraModulesPath, ... }: {
  imports = [
    "${extraModulesPath}/language/go.nix"
  ];

  # Load packages
  packages = [
    a8-scripts.a8-scripts
    my-java
    my-sbt
    nixpkgs.python3
    nixpkgs.gnupg
  ];

  # Set env vars
  env = [
    { name = "KEY"; value = "1"; }
    { name = "SDFSDFG"; eval = "$KEY-xxx"; }
  ];

    # Add commands in the menu
  commands =
    if builtins.currentSystem == "aarch64-darwin" then [] else [
      {
        package = nixpkgs.niv;
        category = "nix";
      }
    ];

})
