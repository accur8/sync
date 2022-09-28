package a8.shared


import java.io.{FileInputStream, FileOutputStream, FileReader, InputStream, OutputStream, PrintStream, Reader}
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Files, LinkOption, Paths, Path => NioPath}
import java.time.LocalDateTime
import SharedImports._
import a8.shared.ops.IteratorOps

import scala.util.Try

object FileSystem {

  lazy val userHome: Directory = dir(System.getProperty("user.home"))

  trait Path {
    def name: String
    def canonicalPath: String
    def exists(): Boolean
    def moveTo(d: Directory): Unit
    def copyTo(d: Directory): Unit
    def relativeTo(directory: Directory): String = directory.asNioPath.relativize(asNioPath).toString
    def asNioPath: NioPath
    def kind: String
    def delete(): Unit
    override def toString = canonicalPath
  }

  trait Directory extends Path {
    def parentOpt: Option[Directory]

    def \(fileName: String): File = file(fileName)
    def \\(subdirName: String): Directory = subdir(subdirName)

    /**
     * makes sure the directory exists
     * @return
     */
    def resolve: Directory = {
      if ( !exists() )
        makeDirectories()
      this
    }

    def makeDirectories(): Unit
    def makeDirectory(): Unit
    def subdirs(): Iterable[Directory]
    def entries(): Iterable[Path]
    def files(): Iterable[File]
    def deleteChildren(): Unit
    def file(filename: String): File
    def subdir(subdirName: String): Directory

  }

  trait File extends Path {

    def parent: Directory
    def write(content: String): Unit
    def write(is: InputStream): Unit
    def withInputStream[A](fn: InputStream => A): A
    def withReader[A](fn: Reader => A): A
    def appendWithOutputStream[A](fn: OutputStream => A): A
    def withOutputStream[A](fn: OutputStream => A): A
    def withPrintStream[A](fn: PrintStream => A): A
    def size(): Long
    def lastModified(): Long

    def readAsString(): String = {
      withReader(_.readFully())
    }

    def readBytes(): Array[Byte] = {
      withInputStream(_.readAllBytes())
    }

    /**
     * reads the file as a string if it exists
     * returns None if it doesn't exist
     */
    def readAsStringOpt(): Option[String] =
      exists()
        .toOption(readAsString())

    def copyTo(target: File): Unit = {
      import java.nio.file.{Files, Paths}
      import java.nio.file.StandardCopyOption.REPLACE_EXISTING
      import language.implicitConversions
      implicit def toNioPath(path: Path): NioPath = Paths.get(path.canonicalPath)
      Files.copy(this, target, REPLACE_EXISTING)
    }
  }

  def dir(path: String): Directory = {
    val startingPath = Paths.get(path)
    realize(startingPath) match {
      case Right(d: Directory) =>
        d
      case Right(p) =>
        sys.error(s"expected a directory got a ${p.kind} -- ${p}")
      case Left(np) =>
        new DirectoryImpl(np)

    }
  }

  def file(path: String): File = {
    realize(Paths.get(path)) match {
      case Right(f: File) =>
        f
      case Left(np) =>
        new FileImpl(np)
      case Right(p) =>
        sys.error(s"expected a file got a ${p.kind} -- ${p}")
    }
  }

  def fromNioPath(nioPath: NioPath): Option[Path] =
    realize(nioPath)
      .toOption

  /**
   * returns a path of the correct type if it exists otherwise None
   */
  def path(path: String): Option[Path] =
    realize(Paths.get(path))
      .toOption

  private def realize(path: NioPath): Either[NioPath,Path] = {
    if ( path.exists() )
      new RealizedNioPath(path.toRealPath(LinkOption.NOFOLLOW_LINKS))
        .resolvePath()
    else {
      Left(path.toAbsolutePath.normalize())
    }
  }

  /**
   * calls to this method should ensure that the passed in path is absolute and normalized
   */
  private def unsafeRealize(path: NioPath): RealizedNioPath = {
    assert ( path.isAbsolute )
    new RealizedNioPath(path)
  }

  private def throwUnsupported(): Nothing = sys.error("this feature is currently not supported")

  /**
   * a RealizedNioPath is a java.nio.file.Path that has been returned by java.nio.file.Path.toRealPath(LinkOption.NOFOLLOW_LINKS)
   */
  private class RealizedNioPath(val nioPath: NioPath) extends AnyVal {
    def resolvePath(): Either[NioPath,Path] =
      Files
        .exists(nioPath)
        .toOption {
          val attributes = readAttributes(nioPath)
          if ( attributes.isRegularFile ) {
            new FileImpl(nioPath, None)
          } else if ( attributes.isDirectory ) {
            new DirectoryImpl(nioPath, None)
          } else if ( attributes.isSymbolicLink ) {
            new SymlinkImpl(nioPath)
          } else if ( attributes.isOther ) {
            new OtherPathImpl(nioPath)
          } else {
            sys.error(s"${nioPath} don't know how to handle attributes ${attributes}")
          }
        }
        .toRight(nioPath)
  }


  def fileSystemCompatibleTimestamp(): String = {
    val now = LocalDateTime.now()
    f"${now.getYear}-${now.getMonthValue}%02d-${now.getDayOfMonth}%02d_${now.getHour}%02d-${now.getMinute}%02d-${now.getSecond}%02d"
  }

  private def readAttributes(nioPath: NioPath): BasicFileAttributes =
    Files.readAttributes[BasicFileAttributes](nioPath, classOf[BasicFileAttributes])

  private abstract class PathImpl(
    val asNioPath: java.nio.file.Path,
  ) {

    lazy val name: String =
      asNioPath.getFileName.toString

    def canonicalPath: String =
      asNioPath.toString

    def attributes(): BasicFileAttributes =
      readAttributes(asNioPath)

    override def equals(obj: Any): Boolean =
      obj match {
        case p: Path =>
          p.canonicalPath === canonicalPath
      }

    override lazy val hashCode = canonicalPath.hashCode

  }

  private class OtherPathImpl(
    nioPath: NioPath,
  )
    extends PathImpl(nioPath)
    with Path
  {

    override def delete(): Unit = Files.delete(nioPath)

    override def exists(): Boolean =
      Try(readAttributes(nioPath).isOther)
        .getOrElse(false)

    override def moveTo(d: Directory): Unit = throwUnsupported()
    override def copyTo(d: Directory): Unit = throwUnsupported()
    override def kind: String = "other"
  }

  private class SymlinkImpl(
    nioPath: NioPath,
  )
    extends PathImpl(nioPath)
    with Path
  {

    override def delete(): Unit = Files.delete(nioPath)

    override def exists(): Boolean =
      Try(readAttributes(nioPath).isOther)
        .getOrElse(false)

    override def moveTo(d: Directory): Unit = throwUnsupported()
    override def copyTo(d: Directory): Unit = throwUnsupported()
    override def kind: String = "symlink"
  }

  private class FileImpl(
    nioPath: java.nio.file.Path,
    preloadedParent: Option[Directory] = None,
  )
    extends PathImpl(nioPath)
    with File
  {

    override def kind: String = "file"

    override lazy val parent: Directory =
      preloadedParent.getOrElse(new DirectoryImpl(nioPath.getParent))

    override def delete(): Unit =
      Files.delete(nioPath)

    override def exists(): Boolean =
      nioPath.toFile.exists()

    override def size(): Long =
      nioPath.toFile.length()

    override def write(content: String): Unit =
      withPrintStream(_.print(content))

    override def lastModified(): Long =
      nioPath.toFile.lastModified()

    override def withInputStream[A](fn: InputStream => A): A = {
      val is = new FileInputStream(nioPath.toFile)
      try {
        fn(is)
      } finally {
        is.close()
      }
    }

    override def withReader[A](fn: Reader => A): A = {
      val reader = new FileReader(nioPath.toFile)
      try {
        fn(reader)
      } finally {
        reader.close()
      }
    }

    override def write(is: InputStream): Unit = {
      try {
        withOutputStream { out =>
          val buffer = new Array[Byte](8192)

          def readNextOpt(): Option[(Array[Byte],Int)] = {
            is.read(buffer) match {
              case -1 =>
                None
              case i =>
                Some(buffer -> i)
            }
          }

          IteratorOps
            .wrap(() => readNextOpt())
            .foreach { case (buffer, count) =>
              if ( count > 0 ) {
                out.write(buffer, 0, count)
              }
            }

        }
      } finally {
        Try(is.close())
      }
    }

    override def withPrintStream[A](fn: PrintStream => A): A = {
      val fos = new FileOutputStream(nioPath.toFile)
      try {
        val out = new PrintStream(new FileOutputStream(nioPath.toFile))
        fn(out)
      } finally {
        Try(fos.close())
      }
    }

    override def withOutputStream[A](fn: OutputStream => A): A =
      withOutputStreamImpl(fn, false)

    def withOutputStreamImpl[A](fn: OutputStream => A, append: Boolean): A = {
      val fos = new FileOutputStream(nioPath.toFile, append)
      try {
        fn(fos)
      } finally {
        Try(fos.close())
      }
    }

    override def appendWithOutputStream[A](fn: OutputStream => A): A =
      withOutputStreamImpl(fn, true)

    override def moveTo(d: Directory): Unit =
      Files.move(asNioPath, d.file(name).asNioPath)

    override def copyTo(d: Directory): Unit =
      Files.copy(asNioPath, d.file(name).asNioPath)

  }


  private class DirectoryImpl(
    nioPath: NioPath,
    preloadedParent: Option[Directory] = None,
  )
    extends PathImpl(nioPath)
    with Directory
  {

    override def kind: String = "dir"

    override def parentOpt: Option[Directory] =
      preloadedParent
        .orElse {
          Option(nioPath.getParent)
            .map(p => new DirectoryImpl(p))
        }

    override def file(fileName: String): File =
      new FileImpl(nioPath.resolve(fileName).toAbsolutePath, Some(this))

    override def subdir(subdirName: String): Directory =
      new DirectoryImpl(nioPath.resolve(subdirName).toAbsolutePath, Some(this))

    override def moveTo(d: Directory): Unit =
      Files.move(nioPath, d.asNioPath)

    override def makeDirectories(): Unit =
      Files.createDirectories(nioPath)

    override def makeDirectory(): Unit =
      Files.createDirectory(nioPath)

    override def subdirs(): Iterable[Directory] =
      entries()
        .collect {
          case d: Directory => d
        }

    override def entries(): Iterable[Path] = {
      Files
        .list(nioPath)
        .iterator()
        .asScala
        .flatMap(p => unsafeRealize(p).resolvePath().toOption) // ??? this could be optimized to house the provide the parent path
        .to(Iterable)
    }

    override def files(): Iterable[File] =
      entries()
        .collect {
          case f: File => f
        }


    override def deleteChildren(): Unit = {
      entries()
        .foreach(_.delete())
    }

    override def delete(): Unit = {
      deleteChildren()
      nioPath.toFile.delete()
    }

    override def copyTo(d: Directory): Unit = {
      val targetDir = d.subdir(name)
      targetDir.makeDirectory()
      entries().foreach(_.copyTo(targetDir))
    }

    override def exists(): Boolean = {
      if ( nioPath.toFile.exists() ) {
        attributes().isDirectory
      } else {
        false
      }
    }

  }

}
