let
  java = "graalvm8-ce";
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
