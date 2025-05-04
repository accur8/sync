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

### Automatic Publish
```shell
nix-shell --command 'sbt clean publishSigned sonatypeBundleRelease'
```

### Manual Publish
- Below are all the granular steps that the automatic publishing does
- You can view what is happening at the [Sonatype Repository Manager](https://s01.oss.sonatype.org/#stagingRepositories) after calling `sonatypePrepare`
```shell
nix-shell
sbt
clean
publishSigned
sonatypePrepare
sonatypeBundleUpload
sonatypeClose
sonatypePromote
```

### Troubleshooting
- If you get something like `[info] gpg: signing failed: Inappropriate ioctl for device`, run
    - Fish: `set -x GPG_TTY (tty)`
    - Bash: `export GPG_TTY=$(tty)`
- If sbt hangs on `signedArtifacts`, kill the `gpg-agent` process
- If you get an error like `no javadoc jar found in folder`, then packageDoc in publishArtifact is disabled
  - Add the following line inside `object Common` in `project/Common.scala`:
    - `override def settings: Seq[Def.Setting[?]] = Seq()`

### Links
- https://central.sonatype.org/publish
- https://github.com/sbt/sbt-pgp
- https://github.com/xerial/sbt-sonatype
- https://s01.oss.sonatype.org
