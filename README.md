# aigest

`aigest` is a CLI tool that packs a Git repository into a nice singular structured XML document so that AI models can easily read it.

## Installation

Currently, this is only packaged for Nix and NixOS.

Feel free to make any contributions packaging it for other package managers and distributions.

### Run without installing:

Run directly from the flake:

```BASH
nix run github:givikuna/aigest -- <arguments>
```

### Installing System-Wide

For Nix flakes:

In your `flake.nix` file:

```Nix
{
    inputs = {
        nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

        aigest.url = "github:givikuna/aigest";
        aigest.inputs.nixpkgs.follows = "nixpkgs";
    };

    outputs = { self, nixpkgs, aigest, ... }: {
        nixosConfigurations.HOSTNAME = nixpkgs.lib.nixosSystem {
            system = "x86_64-linux";
            modules = [
                ./configuration.nix
                {
                    nixpkgs.overlays = [
                        aigest.overlays.default
                    ];
                }
            ];
            specialArgs = { inherit aigest; };
        }
    }
}
```

And in `configuration.nix`:

```Nix
{ pkgs, aigest, ... }: {
    environment.systemPackages = with pkgs; [
        aigest
    ];
}
```

Then you can rebuild.

This also works for Home Manager:

```Nix
{ pkgs, aigest, ... }: {
    home.packages = [
        aigest.packages.${pkgs.system}.default
    ];
}
```

## Usage

You can create a `aigest.json` file at `~/.config/aigest/aigest.json`

Or you can also in the root of your repo.

The one at the root of your repo has a higher hierarchy than the one as a dotfile, as such, the root file is going to take precedence over the dotfile for rule conflicts.

### CLI Options

- `[profile]` - Profile name to run from `aigest.json`
- `-o`, `--output <path>` - Path to write the generated XML file. Parent directories are created automatically
- `d`, `--dir <path>` - Target directory root directory (`.` current directory)
- `c`, `--global-config <path>` - Path to the global file `$XDG_CONFIG_HOME/aigest/aigest.json` (fallback: `~/.config/aigest/aigest.json`)
- `--include-logs` - Include the last 50 Git commits in the output (enabled by Default)
- `--no-logs` - Omit git commits from the output
- `-v`, `--version` - Prints the version
- `-h`, `--help` - Helps You, HOPEFULLY

### Configuration Reference

| Key        | Type      | Default | Description                                                                                                              |
| ---------- | --------- | ------- | ------------------------------------------------------------------------------------------------------------------------ |
| `override` | `boolean` | `false` | When true in a repository `aigest.json`, discards the entire global config (both global base rules and global profiles). |
| `ext`      | `object`  | `{}`    | Rules keyed by file extension.                                                                                           |
| `dir`      | `object`  | `{}`    | Rules keyed by repository-relative directory path.                                                                       |
| `profiles` | `object`  | `{}`    | Named rule sets that can be selected on the CLI.                                                                         |

#### Extension Rules

Key files are extensions.
They are normalized to lowercase with any leading `.` stripped.

Each extension key maps to an object with either a blanket ignore or selective ignore:

| Key                | Type                       | Description                                                                                                                                                       |
| ------------------ | -------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ignore`           | `boolean`                  | If true, ignores all files with this extension everywhere. All other keys on this rule are ignored.                                                               |
| `ignoreAllIn`      | `string[]`                 | List of relative directory paths. Ignores files with this extension anywhere inside these directories. Use `"."` or `""` to match the entire repository.          |
| `ignoreAllRegex`   | `string[]`                 | List of Java regex patterns. Ignores files with this extension if any pattern matches (via partial `find()`) either the filename or the normalized relative path. |
| `ignoreAllRegexIn` | `object (dir -> string[])` | Map of relative directory paths to lists of Java regex patterns. Ignores matching files with this extension only inside the specified directory.                  |

Example:

```JSON
"ext": {
    "lock": {
        "ignore": true
    },
    "json": {
        "ignoreAllIn": ["dist", "fixtures/snapshots"],
        "ignoreAllRegex": ["\\.min\\.json$", "package-lock"],
        "ignoreAllRegexIn": {
            "config": ["^local\\."]
        }
    }
}
```

#### Directory Rules

`dir`

Keys are repository-relative directory paths.
Backslashes are converted to `/`, leading `./` or `/` and trailing `/` are stripped, and `"."` normalizes to `""` (the root of the repo).

Each directory key maps to an object with either a blanket ignore or selective ignore rules:

| Key              | Type                   | Description                                                                                                                                 |
| ---------------- | ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `ignore`         | `boolean`              | If true, ignores this directory and its entire subtree—unless a more specific child directory has a rule with `ignore: false` (or omitted). |
| `ignoreAllOfExt` | `string[]`             | List of extensions (normalized, with or without leading `.`) to ignore anywhere inside this directory.                                      |
| `ignoreAllRegex` | `string[]` or `object` | Regex patterns matched against filename or normalized relative path. Supports two formats (see below).                                      |

##### Two Formats for `dir.<path>.ignoreAllRegex`

It can be an array (any depth):

```JSON
"src": {
    "ignoreAllRegex": ["\\.spec\\.ts$", "__mocks__"]
}
```

Or or an object, the keys of which are non-negative integer strings representing the maximum relative depth to the root of that directory.
Or `Infinity` or `infinity` for unbounded depth.

Example:

```JSON
"src": {
    "ignoreAllRegex": {
        "0": ["^scratch\\.txt$"],
        "1": ["\\.generated\\."],
        "Infinity": ["\\.tmp$"]
    }
}
```

#### Profiles

Each entry under `profiles` is a named configuration block.

Example:

```JSON
"profiles": {
    "review": {
        "override": false,
        "ext": {
            "md": { "ignore": true }
        },
        "dir": {
            "tests": { "ignore": true }
        }
    },
    "docs-only": {
        "override": true,
        "dir": {
            ".": { "ignore": true },
            "docs": {}
        }
    }
}
```

### Example Configuration

```JSON
{
    "override": false,
    "ext": {
        "lock": { "ignore": true },
        "svg": { "ignore": true },
        "json": {
            "ignoreAllIn": ["test/fixtures"],
            "ignoreAllRegex": ["\\.snap\\.json$"]
        }
    },
    "dir": {
        "node_modules": { "ignore": true },
        "build": { "ignore": true },
        "build/reports": {
            "ignoreAllOfExt": ["html"]
        },
        "src": {
            "ignoreAllOfExt": ["map"],
            "ignoreAllRegex": {
                "0": ["^debug\\."],
                "Infinity": ["\\.generated\\."]
            }
        }
    },
    "profiles": {
        "minimal": {
            "override": true,
            "dir": {
                ".": { "ignore": true },
                "src/core": {}
            }
        }
    }
}
```

## Hierarchy of Rules

Rule resolution is two distinct phases.
Config and File.

Config resolution uses the actual `aigest.json` configuration file.
File is the root of the repo `aigest.json` file.

### Global Override Gate

If the repository the `aigest.json` file sets `"override": true` at the top level, the entire global config is discarded before anything else runs (including global profiles).

| Invocation                               | Condition                       | Effective Rule Precedence (Lowest to Highest)        |
| ---------------------------------------- | ------------------------------- | ---------------------------------------------------- |
| `aigest` (no profile → `main`)           | Default                         | Global Base → Repo Base                              |
| `aigest <name>` (profile exists in repo) | `repoProfile.override: true`    | Repo Profile only                                    |
| `aigest <name>` (profile exists in repo) | `repoProfile.override: false`   | Global Base → Global Profile (if any) → Repo Profile |
| `aigest <name>` (profile only in global) | `globalProfile.override: true`  | Global Profile only                                  |
| `aigest <name>` (profile only in global) | `globalProfile.override: false` | Global Base → Global Profile                         |
| `aigest <name>` (not found in either)    | —                               | Error (exits with status `1`)                        |

The top-level `ext` and `dir` rules (base repo file) are only used when running without a profile (`main`).
Unless given rules say otherwise.

Pipeline:

```md
1. Hardcoded .git skip

2. Root .gitignore filter

3. dir rules (directory pruning & nested re-inclusion)

4. dir rules (file-level matching with DirIgnore barrier)

5. ext rules (extension-level matching)

6. Output file self-exclusion

7. Binary file detection (kept in tree, omitted from <files>)
```

## Output Format

This is in `.xml`.
