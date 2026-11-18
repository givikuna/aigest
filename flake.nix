{
  description = "aigest - git ingestion";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
  };

  outputs =
    { self, nixpkgs }:
    let
      for-all-sys = nixpkgs.lib.genAttrs [
        "x86_64-linux"
        "aarch64-linux"
        "x86_64-darwin"
        "aarch64-darwin"
      ];
    in
    {
      packages = for-all-sys (
        system:
        let
          pkgs = nixpkgs.legacyPackages.${system};
          aigestPkg = pkgs.callPackage ./nix/package.nix { };
        in
        {
          aigest = aigestPkg;
          default = aigestPkg;
        }
      );

      apps = for-all-sys (system: {
        aigest = {
          type = "app";
          program = "${self.packages.${system}.aigest}/bin/aigest";
        };
        default = self.apps.${system}.aigest;
      });

      overlays.default = final: prev: {
        aigest = final.callPackage ./nix/package.nix { };
      };

      devShells = for-all-sys (
        system:
        let
          pkgs = nixpkgs.legacyPackages.${system};
        in
        {
          default = pkgs.mkShell {
            buildInputs = with pkgs; [
              groovy
              jdk
              git
            ];
          };
        }
      );
    };
}
