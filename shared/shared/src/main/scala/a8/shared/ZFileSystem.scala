package a8.shared

import zio.{Chunk, Task, ZIO}

import java.nio.file.{Files, LinkOption, Paths, Path => NioPath}
import SharedImports._
import a8.shared.FileSystem.Directory
import a8.shared.ops.IteratorOps
import zio.stream.{ZSink, ZStream}

import java.io.{ByteArrayInputStream, FileOutputStream, OutputStream, PrintStream, Reader}
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDateTime
import scala.util.Try

object ZFileSystem {

  lazy val userHome: Directory = dir(System.getProperty("user.home"))

  import impl._

  type Z[A] = Task[A]

  trait Path {
    lazy val name: String = asNioPath.toFile.getName
    def canonicalPath: Z[String] = zblock(asNioPath.toFile.getCanonicalPath)
    lazy val absolutePath: String = asNioPath.toFile.getAbsolutePath
    def exists: Z[Boolean] = zblock(asNioPath.toFile.exists())
    def moveTo(d: Directory): Z[Unit]
    def copyTo(d: Directory): Z[Unit]
    def relativeTo(directory: Directory): String = directory.asNioPath.relativize(asNioPath).toString
    def asNioPath: NioPath
    def kind: String
    def delete: Z[Unit]
    override def toString = absolutePath
  }


  trait Directory extends Path {
    def parentOpt: Option[Directory]

    def \(fileName: String): File = file(fileName)
    def \\(subdirName: String): Directory = subdir(subdirName)

    /**
     * makes sure the directory exists
     * @return
     */
    def resolve: Z[Directory] = {
      exists
        .flatMap { e =>
          if ( e )
            makeDirectories
          else
            zunit
        }
        .as(this)
    }

    def makeDirectories: Z[Unit]
    def makeDirectory: Z[Unit]
    def subdirs: Z[Iterable[Directory]]
    def entries: Z[Iterable[Path]]
    def files: Z[Iterable[File]]
    def deleteChildren: Z[Unit]
    def file(filename: String): File
    def subdir(subdirName: String): Directory

  }

  trait File extends Path {

    def parent: Directory
    def write(content: String): Z[Unit] = ZIO.writeFile(asNioPath, content)

    def write(is: =>java.io.InputStream): Z[Long] =
      ZStream.fromInputStream(is)
        .run(ZSink.fromFile(asNioPath.toFile))

    def size: Z[Long] = impl.zblock(asNioPath.toFile.length())
    def lastModified: Z[Long] = impl.zblock(asNioPath.toFile.lastModified())

    def readAsStream: XStream[Byte] = ZStream.fromFile(asNioPath.toFile)
    def readAsString: Z[String] = ZIO.readFile(asNioPath)
    def readBytes: Z[Chunk[Byte]] = readAsStream.runCollect

    /**
     * reads the file as a string if it exists
     * returns None if it doesn't exist
     */
    def readAsStringOpt: Z[Option[String]] =
      exists
        .flatMap {
          case true =>
            readAsString
              .map(_.some)
          case false =>
            zsucceed(None)
        }

    def copyTo(target: File): Z[Unit] =
      impl.zblock {
        import java.nio.file.{Files, Paths}
        import java.nio.file.StandardCopyOption.REPLACE_EXISTING
        import language.implicitConversions
  //      implicit def toNioPath(path: Path): NioPath = Paths.get(path.canonicalPath)
        Files.copy(asNioPath, target.asNioPath, REPLACE_EXISTING)
        ()
      }
  }

  def dir(path: String): Directory = {
    val startingPath = Paths.get(path)
    new DirectoryImpl(startingPath)
  }

  def file(path: String): File =
    new FileImpl(Paths.get(path))

  def fromNioPath(nioPath: NioPath): Z[Option[Path]] =
    realize(nioPath)
      .map(_.toOption)

  /**
   * returns a path of the correct type if it exists otherwise None
   */
  def path(path: String): Z[Option[Path]] =
    realize(Paths.get(path))
      .map(_.toOption)

  private def realize(path: NioPath): Z[Either[NioPath, Path]] =
    zblock(
      if (path.exists()) {
        new RealizedNioPath(path.toRealPath(LinkOption.NOFOLLOW_LINKS))
          .unsafeResolvePath()
      } else {
        Left(path.toAbsolutePath.normalize())
      }
    )

  /**
   * calls to this method should ensure that the passed in path is absolute and normalized
   */
  private def unsafeRealize(path: NioPath): RealizedNioPath = {
    assert(path.isAbsolute)
    new RealizedNioPath(path)
  }

  private def throwUnsupported(): Nothing = sys.error("this feature is currently not supported")
  private def zfailUnsupported = zfail(new RuntimeException("this feature is currently not supported"))

  /**
   * a RealizedNioPath is a java.nio.file.Path that has been returned by java.nio.file.Path.toRealPath(LinkOption.NOFOLLOW_LINKS)
   */
  private class RealizedNioPath(val nioPath: NioPath) extends AnyVal {
    def unsafeResolvePath(): Either[NioPath, Path] =
      Files.exists(nioPath)
        .toOption {
          val attributes = unsafeReadAttributes(nioPath)
          if (attributes.isRegularFile) {
            Right(new FileImpl(nioPath))
          } else if (attributes.isDirectory) {
            Right(new DirectoryImpl(nioPath))
          } else {
            sys.error(s"${nioPath} don't know how to handle attributes ${attributes}")
          }
        }
        .getOrElse(Left(nioPath))
  }


  def fileSystemCompatibleTimestamp(): String = {
    val now = LocalDateTime.now()
    f"${now.getYear}-${now.getMonthValue}%02d-${now.getDayOfMonth}%02d_${now.getHour}%02d-${now.getMinute}%02d-${now.getSecond}%02d"
  }

  private def readAttributes(nioPath: NioPath): Z[BasicFileAttributes] =
    zblock(unsafeReadAttributes(nioPath))

  private def unsafeReadAttributes(nioPath: NioPath): BasicFileAttributes =
    Files.readAttributes[BasicFileAttributes](nioPath, classOf[BasicFileAttributes])

  private abstract class PathImpl(
    val asNioPath: java.nio.file.Path,
  ) {
    self: Path =>

    def attributes: Z[BasicFileAttributes] =
      readAttributes(asNioPath)

    override def equals(obj: Any): Boolean =
      obj match {
        case p: Path =>
          p.absolutePath === absolutePath
      }

    override lazy val hashCode = absolutePath.hashCode

  }

//  private class OtherPathImpl(
//    nioPath: NioPath,
//  )
//    extends PathImpl(nioPath)
//      with Path {
//
//    override def delete: Z[Unit] =
//      impl.zblock(Files.delete(nioPath))
//
//    override def exists: Z[Boolean] =
//      impl.zblock(
//        Try(readAttributes(nioPath).isOther)
//          .getOrElse(false)
//      )
//
//    override def moveTo(d: Directory): Z[Unit] =
//      zfailUnsupported
//
//    override def copyTo(d: Directory): Z[Unit] =
//      zfailUnsupported
//
//    override def kind: String = "other"
//  }

//  private class SymlinkImpl(
//    nioPath: NioPath,
//  )
//    extends PathImpl(nioPath)
//      with Path {
//
//    override def delete: Z[Unit] =
//      impl.zblock(
//        Files.delete(nioPath)
//      )
//
//    override def exists: Z[Boolean] =
//      impl.zblock(
//        Try(readAttributes(nioPath).isOther)
//          .getOrElse(false)
//      )
//
//    override def moveTo(d: Directory): Z[Unit] =
//      zfailUnsupported
//
//    override def copyTo(d: Directory): Z[Unit] =
//      zfailUnsupported
//
//    override def kind: String = "symlink"
//
//  }

  private class FileImpl(
    nioPath: java.nio.file.Path
  )
    extends PathImpl(nioPath)
      with File {

    override def kind: String = "file"

    override lazy val parent: Directory =
      new DirectoryImpl(nioPath.getParent)

    override def delete: Z[Unit] =
      impl.zblock(Files.delete(nioPath))

    override def exists: Z[Boolean] =
      impl.zblock(nioPath.toFile.exists())

    override def size: Z[Long] =
      impl.zblock(nioPath.toFile.length())

    override def lastModified: Z[Long] =
      impl.zblock(nioPath.toFile.lastModified())

    override def moveTo(d: Directory): Z[Unit] =
      impl.zblock(Files.move(asNioPath, d.file(name).asNioPath))
        .as(())

    override def copyTo(d: Directory): Z[Unit] =
      impl.zblock(Files.copy(asNioPath, d.file(name).asNioPath))

  }


  private class DirectoryImpl(
    nioPath: NioPath
  )
    extends PathImpl(nioPath)
      with Directory {

    override def kind: String = "dir"

    override def parentOpt: Option[Directory] =
      Option(nioPath.getParent)
        .map(p => new DirectoryImpl(p))

    override def file(fileName: String): File =
      new FileImpl(nioPath.resolve(fileName).toAbsolutePath)

    override def subdir(subdirName: String): Directory =
      new DirectoryImpl(nioPath.resolve(subdirName).toAbsolutePath)

    override def moveTo(d: Directory): Z[Unit] =
      impl.zblock(
        Files.move(asNioPath, d.subdir(name).asNioPath)
      )

    override def makeDirectories: Z[Unit] =
      impl.zblock(
        if (!nioPath.toFile.isDirectory)
          Files.createDirectories(nioPath)
      )

    override def makeDirectory: Z[Unit] =
      impl.zblock(
        if (!nioPath.toFile.isDirectory)
          Files.createDirectory(nioPath)
      )

    override def subdirs: Z[Iterable[Directory]] =
      entries
        .map {
          _.collect {
            case d: Directory => d
          }
        }

    override def entries: Z[Iterable[Path]] =
      exists
        .flatMap {
          case true =>
            impl.zblock(
              Files
                .list(nioPath)
                .iterator()
                .asScala
                .flatMap(p => unsafeRealize(p).unsafeResolvePath().toOption) // ??? this could be optimized to house the provide the parent path
                .to(Iterable)
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
      exists
        .flatMap {
          case true =>
            deleteChildren
              .asZIO(impl.zblock(nioPath.toFile.delete()))
          case false =>
            zunit
        }

    override def copyTo(d: Directory): Z[Unit] = {
      val targetDir = d.subdir(name)
      targetDir
        .makeDirectory
        .asZIO {
          entries
            .map(
              _.map(e => e.copyTo(targetDir)).sequence
            )
        }
    }

    override def exists: Z[Boolean] =
      impl
        .zblock(
          nioPath.toFile.exists()
        )
        .flatMap {
          case true =>
            attributes.map(_.isDirectory)
          case false =>
            zsucceed(false)
        }

  }

  object impl {
    def zblock[A](fn: =>A): Z[A] =
      ZIO.attemptBlocking(fn)
  }

}
