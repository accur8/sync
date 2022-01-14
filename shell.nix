{ pkgs ? import <nixpkgs> {} }:


let
    my-java = pkgs.openjdk11_headless;

    my-scala = pkgs.scala.override { jre = my-java; };
    my-ammonite = pkgs.ammonite_2_13.override { jre = my-java; };

    my-sbt =  pkgs.sbt.override { jre = my-java; };

in

  pkgs.mkShell {
    buildInputs = [
      my-ammonite
      pkgs.exa
      my-java
      my-sbt
      my-scala
    ];
  }
