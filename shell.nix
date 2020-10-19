let
  pkgs = import ./nix;
  hugo = pkgs.callPackage ./nix/hugo.nix {};
in
pkgs.mkShell {
  buildInputs = [
    pkgs.git
    pkgs.openjdk8_headless
    pkgs.sbt
    hugo
  ];
}
