{ pkgs ? import ./nix }:

with pkgs;
mkShell {
  buildInputs = [
    git
    hugo
    openjdk8_headless
    sbt
  ];
}
