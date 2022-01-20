# Accur8 Sync

## Sonatype Deployment

### Prerequisites

#### Sonatype SBT Credentials
Create `~/.sbt/sonatype.credentials`:
```
realm=Sonatype Nexus Repository Manager
host=s01.oss.sonatype.org
user=
password=
```

#### Configure GnuPG
- Install GnuPG
- Generate and publish GnuPG key
    - Follow instructions at https://central.sonatype.org/publish/requirements/gpg
    - Save GnuPG key passphrase, which is needed during the `sbt publishSigned` step

### Publish
```shell
nix-shell --command 'sbt clean publishSigned sonatypeBundleRelease'
```

### Troubleshooting
- If you get something like `[info] gpg: signing failed: Inappropriate ioctl for device`, run
    - Fish: `set -x GPG_TTY (tty)`
    - Bash: `export GPG_TTY=$(tty)`
- If sbt hangs on `signedArtifacts`, kill the `gpg-agent` process

### Links
- https://central.sonatype.org/publish
- https://github.com/sbt/sbt-pgp
- https://github.com/xerial/sbt-sonatype
- https://s01.oss.sonatype.org