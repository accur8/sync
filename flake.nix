{

  inputs = {
    nix-pins.url = "git+ssh://git@git.accur8.io/a8/nix-pins";
  };

  outputs =
    { self, nix-pins, ... }:
    {
      devShells = nix-pins.lib.composeDevShells {
        default =
          { pkgs, frags, ... }:
          [
            (frags.scala_3 { jdk = pkgs.jdk25; })
            (frags.aiCodingAssistants { })
          ];
      };
    };
}
