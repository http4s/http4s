self: super:
{
  hugo = self.buildGoPackage rec {
    name = "hugo-${version}";
    version = "0.27";

    goPackagePath = "github.com/gohugoio/hugo";

    src = self.fetchFromGitHub {
      owner = "gohugoio";
      repo = "hugo";
      rev = "v${version}";
      sha256 = "1r64pwk5g50gwriawmsgza6j8m4jymg8mwgwh1rplpsdfxqdfrbx";
    };

    goDeps = ./hugo-deps.nix;
  };
}
