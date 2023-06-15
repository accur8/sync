package a8.shared

import zio.{Chunk, Task, ZIO}

import java.nio.file.{Files, LinkOption, Paths, Path => NioPath}
import SharedImports._
import a8.shared.FileSystem.Directory
import a8.shared.ZString.ZStringer
import a8.shared.json.JsonCodec
import a8.shared.ops.IteratorOps
import zio.stream.{ZSink, ZStream}

import java.io.{ByteArrayInputStream, FileOutputStream, OutputStream, PrintStream, Reader}
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDateTime
import scala.util.Try

object ZFileSystem {

  lazy val userHome: Directory = dir(System.getProperty("user.home"))
  lazy val root: Directory = dir("/")

  import ZFileSystemImpl._

  type Z[A] = Task[A]

  sealed abstract class SymlinkHandler(val linkOption: Option[LinkOption])
  object SymlinkHandler {
    case object Follow extends SymlinkHandler(Some(LinkOption.NOFOLLOW_LINKS))
    case object NoFollow extends SymlinkHandler(None)
  }

  object SymlinkHandlerDefaults {
    given follow: SymlinkHandler = SymlinkHandler.Follow
    given noFollow: SymlinkHandler = SymlinkHandler.NoFollow
  }

  trait Path {
    lazy val name: String = asJioFile.getName
    def parentOpt: Option[Directory]
    def canonicalPath: Z[String] = zblock(asJioFile.getCanonicalPath)
    def absolutePath: String = asJioFile.getAbsolutePath
    def path = asNioPath.toString
    def exists(implicit symlinkHandler: SymlinkHandler): Z[Boolean]
    def moveTo(d: Directory): Z[Unit]
    def copyTo(d: Directory): Z[Unit]
    def relativeTo(directory: Directory): String = directory.asNioPath.relativize(asNioPath).toString
    def asNioPath: NioPath
    def asJioFile: java.io.File = asNioPath.toFile
    def kind: String
    def delete: Z[Unit]
    def deleteIfExists: Z[Unit]
    def existsAsFile(implicit symlinkHandler: SymlinkHandler): Z[Boolean]
    def existsAsDirectory(implicit symlinkHandler: SymlinkHandler): Z[Boolean]
    def existsAsSymlink: Z[Boolean]
    def existsAsOther(implicit symlinkHandler: SymlinkHandler): Z[Boolean]
    def isAbsolute: Boolean = asNioPath.isAbsolute
    override def toString = absolutePath
  }


  object Directory extends AbstractStringValueCompanion[Directory] {
    override def valueToString(a: Directory): String = a.absolutePath
    override def valueFromString(s: String): Directory = ZFileSystem.dir(s)
  }

  trait Directory extends Path {

    def parentOpt: Option[Directory]

    def \(fileName: String): File = file(fileName)
    def /(subdirName: String): Directory = subdir(subdirName)

    def copyChildrenTo(target: Directory): Z[Unit]

    /**
     * similar to python's Path.join
     * @return
     */
    def join(f: File): File
    def join(d: Directory): Directory
    def join(s: Symlink): Symlink

    /**
     * makes sure the directory exists
     * @return
     */
    def resolve: Z[Directory] =
      exists(SymlinkHandler.Follow)
        .flatMap {
          case true =>
            zunit
          case false =>
            makeDirectories
        }
        .as(this)

    def makeDirectories: Z[Unit]
    def makeDirectory: Z[Unit]
    def subdirs: Z[Iterable[Directory]]
    def entries: Z[Iterable[Path]]
    def files: Z[Iterable[File]]
    def deleteChildren: Z[Unit]

    def file(filename: String): File
    def symlink(filename: String): Symlink
    def subdir(subdirName: String): Directory

  }

  trait HasParent {
    self: Path =>
    def parentOpt: Option[Directory] = parent.some
    def parent: Directory = new DirectoryImpl(asNioPath.getParent)
  }

  object File extends AbstractStringValueCompanion[File] {
    override def valueToString(a: File): String = a.absolutePath
    override def valueFromString(s: String): File = ZFileSystem.file(s)
  }

  trait File extends Path with HasParent {

    def write[R](byteStream: ZStream[R,Throwable,Byte]): zio.ZIO[R,Throwable,Long]

    def write(content: String, overwrite: Boolean = true): Z[Unit] =
      parent
        .resolve
        .asZIO(
          ZIO.writeFile(asNioPath, content)
        )

    def write(is: =>java.io.InputStream): Z[Long] =
      parent
        .resolve
        .asZIO(
          ZStream.fromInputStream(is)
            .run(ZSink.fromFile(asNioPath.toFile))
        )

    def size: Z[Long] = zblock(asNioPath.toFile.length())
    def lastModified: Z[Long] = zblock(asNioPath.toFile.lastModified())

    def readAsStream: XStream[Byte] = ZStream.fromFile(asNioPath.toFile)
    def readAsString: Z[String] = ZIO.readFile(asNioPath)
    def readBytes: Z[Chunk[Byte]] = readAsStream.runCollect

    /**
     * reads the file as a string if it exists
     * returns None if it doesn't exist
     */
    def readAsStringOpt: Z[Option[String]] =
      exists(SymlinkHandler.Follow)
        .flatMap {
          case true =>
            readAsString
              .map(_.some)
          case false =>
            zsucceed(None)
        }

    def copyTo(target: File): Z[Unit] =
      zblock {
        import java.nio.file.{Files, Paths}
        import java.nio.file.StandardCopyOption.REPLACE_EXISTING
        import language.implicitConversions
  //      implicit def toNioPath(path: Path): NioPath = Paths.get(path.canonicalPath)
        Files.copy(asNioPath, target.asNioPath, REPLACE_EXISTING): @scala.annotation.nowarn
        ()
      }
  }

  trait Symlink extends Path with HasParent {
    def readTarget: Z[String]
    def writeTarget(target: String): Z[Unit]
    def asFile: File
    def asDirectory: Directory
  }

  trait Other extends Path with HasParent {
  }

  def symlink(path: String): Symlink = {
    val startingPath = Paths.get(path)
    new SymlinkImpl(startingPath)
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

  def fileSystemCompatibleTimestamp(): String = {
    val now = LocalDateTime.now()
    f"${now.getYear}-${now.getMonthValue}%02d-${now.getDayOfMonth}%02d_${now.getHour}%02d-${now.getMinute}%02d-${now.getSecond}%02d"
  }

}
