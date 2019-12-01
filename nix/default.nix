let
  hostPkgs = import <nixpkgs> {};
  pinnedVersion = hostPkgs.lib.importJSON ./nixpkgs-version.json;
  pinnedPkgs = hostPkgs.fetchFromGitHub {
    owner = "NixOS";
    repo = "nixpkgs-channels";
    inherit (pinnedVersion) rev sha256;
  };
  overlay = import ./hugo.nix;
in import pinnedPkgs {
  overlays = [ overlay ];
}
