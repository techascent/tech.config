# CI

The `ci.yml` workflow runs on every push/PR to `master`. It:

1. Installs OS tools (Java, Node, Vault, Babashka) via `scripts/install --no-deps`
2. Opens the GitHub Actions runner IP on the Vault security group so it can authenticate
3. Fetches AWS credentials from Vault for the private S3 Maven repo
4. Caches Maven (`~/.m2`) and npm (`node_modules`) dependencies between runs
5. Installs project dependencies via `scripts/install`
6. Runs `scripts/test` (JVM, ClojureScript, and Babashka test suites)
7. Revokes the runner IP from the Vault security group

## Required secrets

| Secret | Purpose |
|---|---|
| `GH_ACTIONS_AWS_ACCESS_KEY_ID` | AWS key for managing the Vault security group |
| `GH_ACTIONS_AWS_SECRET_ACCESS_KEY` | AWS secret for managing the Vault security group |
| `VAULT_GITHUB_TOKEN` | GitHub token for `vault login -method=github` |

## Composite actions

- **`vault-allow`** — adds the runner's IP to the Vault SG and logs into Vault
- **`vault-revoke`** — removes the runner's IP from the Vault SG (runs even if tests fail)
