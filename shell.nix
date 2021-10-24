let
  java = "openjdk8_headless";
  pkgs = import ./nix/pkgs.nix { inherit java; };
in
pkgs.mkShell {
  buildInputs = [
    pkgs.git
    pkgs.nodejs-16_x
    pkgs.${java}
    pkgs.sbt
    pkgs.yarn
  ];
}
