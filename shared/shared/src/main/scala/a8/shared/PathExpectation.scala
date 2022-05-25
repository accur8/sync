package a8.shared


import a8.shared.FileSystem.Directory
import SharedImports._
import cats.Eq

import java.nio.file.{Files, LinkOption, Path => NioPath, Paths => NioPaths}

object PathExpectation {


  def emptyDirectory[F[_] : Sync](dir: String): F[Directory] =
    Sync[F].blocking {
      val nioPath = NioPaths.get(dir)
      FileSystem.path(dir) match {
        case Some(d: Directory) =>
          emptyDirectory[F](d)
        case Some(p) =>
          p.delete()
          emptyDirectory[F](FileSystem.dir(dir))
        case None =>
          Sync[F].pure(FileSystem.dir(dir))
      }
    }.flatten


  def emptyDirectory[F[_] : Sync](dir: Directory): F[Directory] =
    Sync[F].blocking {
      if ( dir.exists() ) {
        dir.deleteChildren()
      } else {
        dir.makeDirectories()
      }
      dir
    }


  def symlinkWithContents[F[_] : Sync](symLink: NioPath, contents: NioPath): F[Boolean] = {
    Sync[F].blocking {
      implicit val pathEq: Eq[NioPath] = Eq.fromUniversalEquals[java.nio.file.Path]
      val createSymLink =
        if ( Files.exists(symLink, LinkOption.NOFOLLOW_LINKS) ) {
          if ( Files.isSymbolicLink(symLink) ) {
            val actualSymlinkContents = Files.readSymbolicLink(symLink)
            if ( contents =!= actualSymlinkContents) {
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
