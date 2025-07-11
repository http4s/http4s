{
  description = "Provision a dev environment";

  inputs = {
    typelevel-nix.url = "github:typelevel/typelevel-nix";
    nixpkgs.follows = "typelevel-nix/nixpkgs";
    flake-utils.follows = "typelevel-nix/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils, typelevel-nix }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          overlays = [ typelevel-nix.overlays.default ];
        };

        mkShell = jdk: pkgs.devshell.mkShell {
          imports = [ typelevel-nix.typelevelShell ];
          name = "http4s";
          typelevelShell = {
            jdk.package = jdk;
            nodejs.enable = true;
            native.enable = true;
            native.libraries = [ pkgs.zlib pkgs.s2n-tls pkgs.openssl pkgs.pkg-config ];
          };
          env = [{
            name = "PKG_CONFIG_PATH";
            value ="${pkgs.openssl.dev}/lib/pkgconfig";
          }];
        };
      in
      rec {
        devShell = mkShell pkgs.jdk8;

        devShells = {
          "temurin@8" = mkShell pkgs.temurin-bin-8;
          "temurin@11" = mkShell pkgs.temurin-bin-11;
          "temurin@17" = mkShell pkgs.temurin-bin-17;
        };
      }
    );
}
