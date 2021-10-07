let
  java = "openjdk8_headless";
  pkgs = import ./nix/pkgs.nix { inherit java; };
  hugo = pkgs.callPackage ./nix/hugo.nix {};
in
pkgs.mkShell {
  buildInputs = [
    pkgs.git
    hugo
    pkgs.nodejs-16_x
    pkgs.${java}
    pkgs.sbt
    pkgs.yarn
  ];
}
