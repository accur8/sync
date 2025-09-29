{
  description = "Project using centralized nix-pins";

  inputs = {
    nix-pins.url = "git+ssh://git@git.accur8.net/a8/nix-pins";
    nixpkgs.follows = "nix-pins/nixpkgs";
    devshell.follows = "nix-pins/devshell";
  };

  outputs = { self, nix-pins, nixpkgs, devshell }:
  {
    devShells = nix-pins.lib.forEachSystem (system:
      let
        pkgs = nix-pins.pkgsFor system;
        # Add any project-specific packages or overrides here
      in {
        default = nix-pins.lib.shells.scala3 {
          inherit pkgs;
          # Add project-specific packages here:
          # extraInputs = [ pkgs.postgresql ];
        };
      });
  };
}
