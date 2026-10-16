# Releasing

Pushing a tag `v<version>` runs `.github/workflows/release.yml`. The workflow uploads that version to the Central Portal and creates a GitHub release with notes generated from the merged pull requests and commits since the previous tag.

1. Set the version in the README's install snippet to the one being released and merge that change.
2. Tag the merged commit and push the tag:

   ```sh
   git tag v0.1.0-dev01
   git push origin v0.1.0-dev01
   ```

3. Open the deployment in the [Central Portal](https://central.sonatype.com/publishing/deployments). After the Portal validates it, press **Publish**. The artifacts reach Maven Central within about half an hour.

The GitHub release appears as soon as the upload succeeds, before **Publish**.

## Versions

A version with a hyphen becomes a pre-release on GitHub. Pre-release suffixes follow the order Gradle uses to compare versions: `-dev01` for experimental builds, then `-alpha01`, `-beta01`, and `-rc01`. Gradle ranks `dev` below every other suffix, so `0.1.0-dev02` < `0.1.0-alpha01` < `0.1.0`.

A published version can never be changed or removed, so a fix needs a new version.

## When a deployment fails

**Drop** in the Central Portal discards a deployment that failed validation or looks wrong. To retry the same version, also delete its GitHub release and tag, then push the tag again:

```sh
gh release delete v<version> --cleanup-tag
git tag -d v<version>
```

## Secrets and the signing key

The workflow reads four repository secrets:

| Secret | Value |
|---|---|
| `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD` | A Central Portal user token for the `dev.seungmin` namespace |
| `SIGNING_IN_MEMORY_KEY` | The signing key's secret key, as `gpg --export-secret-keys <key id> \| base64` or as ASCII armor |
| `SIGNING_IN_MEMORY_KEY_PASSWORD` | The signing key's passphrase |

Releases are signed with the key `5CB3 949B 3024 99F5 05CD 76C5 A2B1 D17D 04E9 7B40`. Maven Central checks signatures against the public key on `keyserver.ubuntu.com` and `keys.openpgp.org`. The key has an expiry date, and an upload signed after it fails validation. To extend it, run `gpg --quick-set-expire <fingerprint> 2y` and send the public key to both servers again; the secrets stay the same.
