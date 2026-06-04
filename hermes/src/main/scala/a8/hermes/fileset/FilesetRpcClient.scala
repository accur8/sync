package a8.hermes.fileset

import a8.hermes.core.Mailbox
import a8.hermes.proto.fileset.fileset.{PullRequest, PullResponse, PullRevisionRequest, PullRevisionResponse}
import a8.hermes.rpc.RpcClient
import a8.shared.app.Ctx

import scala.concurrent.duration.{DurationInt, FiniteDuration}

/**
 * One file of a pulled fileset revision: its repo-relative path and raw bytes. Neutral shape so the bus
 * library stays app-agnostic — checkpoint adapts these into its own `SourceFileSet` of `.cp` sources.
 */
case class FilesetFile(path: String, content: Array[Byte])

/** The files of one immutable fileset revision, as returned by [[FilesetRpcClient.pullRevision]]. */
case class FilesetFiles(filesetUid: String, filesetName: String, revisionUid: String, files: Seq[FilesetFile])

/**
 * Client for godev's `fileset.v1` RPC — pulls the (immutable) files of a fileset revision over the mesh.
 * Mirrors [[a8.hermes.jdbcrpc.DbRpcClient]]: wraps `RpcClient.callTyped`, addressed to the service mailbox
 * that hosts the fileset engine (the continuum mailbox in the current topology). Generic — any JVM service
 * on the mesh can fetch a revision; checkpoint uses it to load a program's `.cp` sources.
 *
 * Implementations MUST throw on RPC failure (timeout / transport / firewall) rather than returning empty.
 */
trait FilesetRpcClient {

  /**
   * Pull the files of one fileset revision. `filesetUid` may be empty when only the revision is known;
   * `revisionUid` pins the immutable content.
   */
  def pullRevision(filesetUid: String, revisionUid: String): FilesetFiles

  /**
   * Pull a fileset by NAME, resolving to its current latest revision. Used to run a program by a stable
   * name (e.g. "healthchecks") rather than a pinned revision UID. The returned `revisionUid` is the
   * concrete revision the name resolved to.
   */
  def pull(filesetName: String): FilesetFiles

}

object FilesetRpcClient {

  /** RPC endpoints registered by godev `pkg/rpc/fileset/schema.go`. */
  val PullRevisionEndpoint = "fileset.v1.PullRevision"
  val PullEndpoint = "fileset.v1.Pull"

  def apply(rpcClient: RpcClient, filesetServiceMailbox: Mailbox.MailboxAddress, defaultTimeout: FiniteDuration = 30.seconds)(using Ctx): FilesetRpcClient =
    new FilesetRpcClient {
      override def pull(filesetName: String): FilesetFiles =
        rpcClient.callTyped[PullRequest, PullResponse](
          targetMailbox = filesetServiceMailbox,
          endpoint = PullEndpoint,
          request = PullRequest(filesetName = filesetName),
          timeout = Some(defaultTimeout),
        ) match {
          case Some(resp) if resp.errorMessage.nonEmpty =>
            throw new FilesetRpcException(s"fileset Pull error for '$filesetName': ${resp.errorMessage}")
          case Some(resp) =>
            FilesetFiles(
              filesetUid = resp.filesetUid,
              filesetName = resp.filesetName,
              revisionUid = resp.revisionUid,
              files = resp.entries.map(e => FilesetFile(path = e.path, content = e.content.toByteArray)),
            )
          case None =>
            throw new FilesetRpcException(s"fileset Pull RPC failed (timeout/transport/firewall) for '$filesetName'")
        }

      override def pullRevision(filesetUid: String, revisionUid: String): FilesetFiles =
        rpcClient.callTyped[PullRevisionRequest, PullRevisionResponse](
          targetMailbox = filesetServiceMailbox,
          endpoint = PullRevisionEndpoint,
          request = PullRevisionRequest(filesetUid = filesetUid, revisionUid = revisionUid),
          timeout = Some(defaultTimeout),
        ) match {
          case Some(resp) if resp.errorMessage.nonEmpty =>
            throw new FilesetRpcException(s"fileset PullRevision error for revision $revisionUid: ${resp.errorMessage}")
          case Some(resp) =>
            FilesetFiles(
              filesetUid = resp.filesetUid,
              filesetName = resp.filesetName,
              revisionUid = resp.revisionUid,
              files = resp.entries.map(e => FilesetFile(path = e.path, content = e.content.toByteArray)),
            )
          case None =>
            throw new FilesetRpcException(s"fileset PullRevision RPC failed (timeout/transport/firewall) for revision $revisionUid")
        }
    }

  class FilesetRpcException(message: String) extends RuntimeException(message)

}
