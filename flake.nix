{
  description = "sync";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  inputs.a8-scripts.url = "github:zimbatm/a8-scripts/nix-init";
  inputs.a8-scripts.inputs.nixpkgs.follows = "nixpkgs";

  outputs = { self, nixpkgs, a8-scripts }:
    let eachSystem = nixpkgs.lib.genAttrs [ "x86_64-linux" ]; in
    {
      devShell = eachSystem (system:
        let
          pkgs = nixpkgs.legacyPackages.${system};
          my-java = pkgs.openjdk11_headless;

          my-scala = pkgs.scala.override { jre = my-java; };
          my-ammonite = pkgs.ammonite_2_13.override { jre = my-java; };

          my-sbt = pkgs.sbt.override { jre = my-java; };
        in
        pkgs.mkShell {
          buildInputs = [
            my-ammonite
            pkgs.exa
            my-java
            my-sbt
            my-scala
            a8-scripts.packages.${system}.a8-scripts
          ];
        }
      );
    };
}
