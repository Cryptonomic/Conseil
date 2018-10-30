Publishing the project to Sonatype and Maven Central
=====================================================

The project relies on some commonly-used sbt plugins to produce artifacts and publish them on public repositories

- [sbt-pgp](https://www.scala-sbt.org/sbt-pgp/) is used to sign the artifacts and publish them
- [sbt-sonatype](https://github.com/xerial/sbt-sonatype) add some command-line tooling to easily interact with sonatype

Instructions are detailed in the [sbt documentation](https://www.scala-sbt.org/release/docs/Using-Sonatype.html) and will be summarized here with specifics of this project.

## Signing

We publish signed artifacts using the sbt-pgp plugin.

<big>_You need to have the gnu-pgp tool installed and visible from the shell `path` to properly sign the artifacts._</big>

If `gpg` is not in the path, you need to add the following line to the `build.sbt` (or better yet, to a global custom `xxx.sbt` file):
```scala
gpgCommand := "/mypath/to/gpg"
```

In case you're unable to have `gpg` running locally, you can _disable the plugin integration_ with the command line tool and use the plugin's internal _Bouncy Castle_ implementation.
Change the `build.sbt` to do that:
```scala
useGpg := false
```

## Credentials

The `sbt-sonatype` plugin is already availble the project

You need to tell where the sonatype credentials are stored. To do so, add a global `~/.sbt/1.0/sonatype.sbt` build definition with the following:
```scala
credentials += Credentials(Path.userHome / ".sbt" / "sonatype-cryptonomic.credentials")
```

This instructs the plugin to look for your Sonatype credentials in the custom `~/.sbt/sonatype-cryptonomic.credentials` file, that will contain the following:
```
realm=Sonatype Nexus Repository Manager
host=oss.sonatype.org
user=<sonatype-user>
password=<sonatype-password>
```

Of course you need to have the proper credentials for a registered Sonatype Account for the `tech.cryptonomic` group-id

This step should allow to `publishSigned` using the pgp plugin

### note
Using this configuration allows different local configurations of where and how to store the credentials differently, without tying it into the project itself.
Please remember that now you'll publish globally using those credentials. When working on a different project, you simply need to change the credentials reference to add different values.

## Publishing steps
With the previous steps taken care of, the regular publishing flow will be

 - `publishSigned` to deploy your artifact to staging repository at Sonatype.
 - `sonatypeRelease` do `sonatypeClose` and `sonatypePromote` in one step.
   - `sonatypeClose` closes your staging repository at Sonatype. This step verifies Maven central sync requirement, GPG-signature, javadoc and source code presence, pom.xml settings, etc.
    - `sonatypePromote` command verifies the closed repository so that it can be synchronized with Maven central.

Note: If your project version has `SNAPSHOT` suffix, your project will be published to the snapshot repository of Sonatype, and you cannot use sonatypeRelease command.

Additional [commands](https://github.com/xerial/sbt-sonatype#available-commands) are available

## Further references
You can find detailed information here:

 - [sbt-sonatype instructions](https://github.com/xerial/sbt-sonatype)
 - [sbt-pgp instructions](https://www.scala-sbt.org/sbt-pgp/usage.html)
 - [sbt docs on sonatype publishing](https://www.scala-sbt.org/release/docs/Using-Sonatype.html)
 - [sbt docs on publishing artifacts](https://www.scala-sbt.org/1.x/docs/Publishing.html)