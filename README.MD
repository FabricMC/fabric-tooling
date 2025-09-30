# Fabric Tooling monorepo

This monorepo contains a selection of tools and libraries that are used by the Fabric Project.
The licenses for each project can be found in the `LICENSE` file of each subdirectory.

## Dev Launch Injector

Injects version dependent args and properties to avoid having to rewrite IDE run configs

## Fabric Log4j Util

Forked from [TerminalConsoleAppender](https://github.com/Minecrell/TerminalConsoleAppender/) under the MIT license to provide LoggerNamePatternSelector as used in a mod development environment.

#### Migrating existing git repos with commit history

1. Install [git-filter-repo](https://github.com/newren/git-filter-repo) with `brew install git-filter-repo`
2. Clone a fresh copy of the source and target repo
3. Filter out everything other than src/ and move /src to the target sub dir - `git filter-repo --path src/ --path-rename src:dev-launch-injector/src`
4. Change dir to fabric-tooling
5. `git remote add migrate <file path to other repo>`
6. `git pull migrate --allow-unrelated-histories main --no-rebase`
