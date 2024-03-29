package a8.shared


import a8.shared.ZFileSystem.*
import zio.ZIO

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Files, Paths, Path as NioPath}
import SharedImports.*
import zio.stream.{ZSink, ZStream}

object ZFileSystemImpl {

  def readAttributes(nioPath: NioPath)(implicit symlinkHandler: SymlinkHandler): Z[Option[BasicFileAttributes]] =
    zblock(unsafeReadAttributes(nioPath))

  def unsafeReadAttributes(nioPath: NioPath)(implicit symlinkHandler: SymlinkHandler): Option[BasicFileAttributes] =
    Files
      .exists(nioPath)
      .toOption(
        Files.readAttributes[BasicFileAttributes](nioPath, classOf[BasicFileAttributes])
      )

  def zblock[A](fn: => A): Z[A] =
    ZIO.attemptBlocking(fn)

  def zblockdefer[A](fn: => Z[A]): Z[A] =
    ZIO.attemptBlocking(fn)
      .flatten

  protected abstract class PathImpl(
    val asNioPath: java.nio.file.Path,
  ) {
    self: Path =>

    override def deleteIfExists: Z[Unit] =
      exists(SymlinkHandler.NoFollow)
        .flatMap {
          case true =>
            delete
          case false =>
            zunit
        }

    override def existsAsFile(implicit symlinkHandler: SymlinkHandler): Z[Boolean] =
      attributes
        .map(_.exists(_.isRegularFile))

    override def existsAsDirectory(implicit symlinkHandler: SymlinkHandler): Z[Boolean] =
      attributes
        .map(_.exists(_.isDirectory))

    override def existsAsSymlink: Z[Boolean] = {
      attributes(SymlinkHandler.NoFollow)
        .map(_.exists(_.isSymbolicLink))
    }

    override def existsAsOther(implicit symlinkHandler: SymlinkHandler): Z[Boolean] =
      attributes
        .map(_.exists(_.isOther))

    final override def exists(implicit symlinkHandler: SymlinkHandler): Z[Boolean] =
      symlinkHandler.linkOption match {
        case None =>
          zblock(Files.exists(asNioPath))
        case Some(lo) =>
          zblock(Files.exists(asNioPath, lo))
      }

    def attributes(implicit symlinkHandler: SymlinkHandler): Z[Option[BasicFileAttributes]] =
      readAttributes(asNioPath)

    override def equals(obj: Any): Boolean =
      obj match {
        case p: Path =>
          p.absolutePath === absolutePath
      }

    override lazy val hashCode = absolutePath.hashCode

  }

  class OtherPathImpl(
    nioPath: NioPath,
  )
    extends PathImpl(nioPath)
      with Other {

    override def delete: Z[Unit] =
      zblock(Files.delete(nioPath))

    override def moveTo(d: Directory): Z[Unit] =
      zfailUnsupported

    override def copyTo(d: Directory): Z[Unit] =
      zfailUnsupported

    override def kind: String = "other"
  }

  class SymlinkImpl(
    nioPath: NioPath,
  )
    extends PathImpl(nioPath)
      with Symlink {

    override def readTarget: Z[String] =
      zblock(
        Files
          .readSymbolicLink(asNioPath)
          .toString
      )

    override def writeTarget(target: String): Z[Unit] =
      parent.makeDirectories
        .asZIO(
          zblock(
            Files.createSymbolicLink(asNioPath, Paths.get(target)): @scala.annotation.nowarn
          )
        )

    override def asFile: File =
      file(path)

    override def asDirectory: Directory =
      dir(path)

    override def delete: Z[Unit] =
      zblock(
        Files.delete(nioPath)
      )

    override def moveTo(d: Directory): Z[Unit] =
      zfailUnsupported

    override def copyTo(d: Directory): Z[Unit] =
      zfailUnsupported

    override def kind: String = "symlink"

  }

  class FileImpl(
    nioPath: java.nio.file.Path
  )
    extends PathImpl(nioPath)
      with File
  { self =>

    override def kind: String = "file"

    override def parentOpt: Option[Directory] = parent.some

    override def delete: Z[Unit] =
      zblock(Files.delete(nioPath))

    override def size: Z[Long] =
      zblock(nioPath.toFile.length())

    override def lastModified: Z[Long] =
      zblock(nioPath.toFile.lastModified())

    override def moveTo(d: Directory): Z[Unit] =
      zblock(Files.move(asNioPath, d.file(name).asNioPath))
        .as(())

    override def copyTo(d: Directory): Z[Unit] =
      zblock(Files.copy(asNioPath, d.file(name).asNioPath): @scala.annotation.nowarn)

    override def write[R](byteStream: ZStream[R, Throwable, Byte]): ZIO[R, Throwable, Long] =
      byteStream.run(ZSink.fromFile(asJioFile))
  }


  class DirectoryImpl(
    nioPath: NioPath
  )
    extends PathImpl(nioPath)
      with Directory {

    override def kind: String = "dir"

    override def join(f: File): File =
      if (f.isAbsolute)
        f
      else
        file(f.path)

    override def join(d: Directory): Directory =
      if (d.isAbsolute)
        d
      else
        subdir(d.path)

    override def join(s: Symlink): Symlink =
      if (s.isAbsolute)
        s
      else
        symlink(s.path)

    override def symlink(filename: String): Symlink =
      new SymlinkImpl(asNioPath.resolve(filename))

    override def parentOpt: Option[Directory] =
      Option(nioPath.getParent)
        .map(p => new DirectoryImpl(p))

    override def file(fileName: String): File =
      new FileImpl(nioPath.resolve(fileName).toAbsolutePath)

    override def subdir(subdirName: String): Directory =
      new DirectoryImpl(nioPath.resolve(subdirName).toAbsolutePath)

    override def moveTo(d: Directory): Z[Unit] =
      zblock(
        Files.move(asNioPath, d.subdir(name).asNioPath): @scala.annotation.nowarn
      )

    override def makeDirectories: Z[Directory] =
      zblock(
        if (!nioPath.toFile.isDirectory)
          Files.createDirectories(nioPath): @scala.annotation.nowarn
      ).as(this)

    override def makeDirectory: Z[Directory] =
      zblock(
        if (!nioPath.toFile.isDirectory)
          Files.createDirectory(nioPath): @scala.annotation.nowarn
      ).as(this)

    override def subdirs: Z[Iterable[Directory]] =
      entries
        .map {
          _.collect {
            case d: Directory => d
          }
        }

    override def entries: Z[Iterable[Path]] =
      exists(SymlinkHandler.Follow)
        .flatMap {
          case true =>
            zblockdefer(
              Files
                .list(nioPath)
                .iterator()
                .asScala
                .map(p =>
                  realizePath(p, assumeExists = true)
                    .map(_.toOption)
                )
                .to(Iterable)
                .sequence
                .map(_.flatten)
            )
          case false =>
            zsucceed(Iterable.empty)
        }

    override def files: Z[Iterable[File]] =
      entries
        .map {
          _.collect {
            case f: File => f
          }
        }


    override def deleteChildren: Z[Unit] =
      entries
        .flatMap(_.map(_.delete).sequence)
        .as(())

    override def delete: Z[Unit] =
      exists(SymlinkHandler.NoFollow)
        .flatMap {
          case true =>
            deleteChildren
              .asZIO(zblock(nioPath.toFile.delete()): @scala.annotation.nowarn)
          case false =>
            zunit
        }


    override def copyTo(d: Directory): Z[Unit] = {
      val targetDir = d.subdir(name)
      targetDir
        .makeDirectories
        .asZIO {
          entries
            .flatMap(
              _.map(e => e.copyTo(targetDir))
                .sequence
            )
        }
        .as(())
    }

    override def copyChildrenTo(targetDir: Directory): Z[Unit] = {
      entries
        .flatMap(
          _.map(e => e.copyTo(targetDir))
            .sequence
        )
        .as(())
    }

  }

  def realize(path: NioPath): Z[Either[NioPath, Path]] =
    realizePath(path, assumeExists = false)
      .map {
        case Right(p) =>
          Right(p)
        case Left(np) =>
          Left(np.toAbsolutePath.normalize())
      }

  def zfailUnsupported: ZIO[Any,RuntimeException,Nothing] = zfail(new RuntimeException("this feature is currently not supported"))

  /**
   * a RealizedNioPath is a java.nio.file.Path that has been returned by java.nio.file.Path.toRealPath(LinkOption.NOFOLLOW_LINKS)
   */
  private def realizePath(nioPath: NioPath, assumeExists: Boolean): Z[Either[NioPath, Path]] =
    zblock {
      val exists = assumeExists || Files.exists(nioPath)
      exists
        .toOption(unsafeReadAttributes(nioPath)(SymlinkHandler.NoFollow))
        .flatten
        .map { attributes =>
          if (attributes.isRegularFile) {
            Right(new FileImpl(nioPath))
          } else if (attributes.isDirectory) {
            Right(new DirectoryImpl(nioPath))
          } else if (attributes.isSymbolicLink) {
            Right(new SymlinkImpl(nioPath))
          } else if (attributes.isOther) {
            Right(new OtherPathImpl(nioPath))
          } else {
            Left(nioPath)
          }
        }
        .getOrElse(Left(nioPath))
    }

}
