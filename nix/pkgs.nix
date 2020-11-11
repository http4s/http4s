{ java }:

let
  hostPkgs = import <nixpkgs> { };
  pinnedVersion = hostPkgs.lib.importJSON ./nixpkgs-version.json;
  pinnedPkgs = hostPkgs.fetchFromGitHub {
    owner = "NixOS";
    repo = "nixpkgs-channels";
    inherit (pinnedVersion) rev sha256;
  };
  config = {
    packageOverrides = p: {
      sbt = p.sbt.override {
        jre = p.${java};
      };
    };
  };
in
import pinnedPkgs { inherit config; }
