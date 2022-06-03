package a8.shared


import a8.shared.FileSystem.Directory
import SharedImports._
import cats.Eq
import zio._

import java.nio.file.{Files, LinkOption, Path => NioPath, Paths => NioPaths}

object PathExpectation {


  def emptyDirectory(dir: String): Task[Directory] = {
    ZIO.blocking {
      val nioPath = NioPaths.get(dir)
      FileSystem.path(dir) match {
        case Some(d: Directory) =>
          emptyDirectory(d)
        case Some(p) =>
          p.delete()
          emptyDirectory(FileSystem.dir(dir))
        case None =>
          ZIO.succeed(FileSystem.dir(dir))
      }
    }
  }


  def emptyDirectory(dir: Directory): Task[Directory] =
    ZIO.attemptBlocking {
      if ( dir.exists() ) {
        dir.deleteChildren()
      } else {
        dir.makeDirectories()
      }
      dir
    }


  def symlinkWithContents(symLink: NioPath, contents: NioPath): Task[Boolean] = {
    ZIO.attemptBlocking {
      implicit val pathEq = Eq.fromUniversalEquals[java.nio.file.Path]
      val createSymLink =
        if ( Files.exists(symLink, LinkOption.NOFOLLOW_LINKS) ) {
          if ( Files.isSymbolicLink(symLink) ) {
            val actualSymlinkContents = Files.readSymbolicLink(symLink)
            if ( contents != actualSymlinkContents) {
              Files.delete(symLink)
              true
            } else {
              false
            }
          } else {
            FileSystem
              .path(symLink.toString)
              .foreach(_.delete())
            true
          }
        } else {
          true
        }
      if ( createSymLink ) {
        Files.createSymbolicLink(symLink, contents)
        true
      } else {
        false
      }
    }
  }



}
