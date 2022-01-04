let
  java = "openjdk8_headless";
  pkgs = import ./nix/pkgs.nix { inherit java; };
  hugo = pkgs.callPackage ./nix/hugo.nix {};
in
pkgs.mkShell {
  buildInputs = [
    pkgs.git
    hugo
    pkgs.${java}
    pkgs.sbt
  ];
}
