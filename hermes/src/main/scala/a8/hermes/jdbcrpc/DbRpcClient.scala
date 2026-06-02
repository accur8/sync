package a8.hermes.jdbcrpc

import a8.hermes.core.Mailbox
import a8.hermes.proto.db.db.{QueryRequest, QueryResponse}
import a8.hermes.rpc.RpcClient
import a8.shared.app.Ctx

import scala.concurrent.duration.DurationInt

/**
 * Minimal client abstraction for the godev "dbaccess" SQL-firewall RPC, as consumed by [[RpcConn]].
 *
 * Keeping this a trait (rather than binding directly to the concrete hermes RpcClient) lets [[RpcConn]]
 * be unit-tested with a fake, and keeps the JDBC-shaped Conn layer decoupled from the mailbox transport.
 *
 * v1 exposes only `query` — Mapper generates INSERT/UPDATE/DELETE as SqlString and those run through the
 * firewall's `Query` endpoint too. Structured Fetch/Insert/Update/Delete/Upsert wrappers are a later upgrade.
 *
 * Implementations MUST throw on RPC failure (timeout / transport / firewall error) rather than returning an
 * empty response — an empty `QueryResponse` means "query ran, zero rows", which a failed call must not mimic.
 */
trait DbRpcClient {

  /** Execute a SQL statement (SELECT or DML) through the firewall and return its response. */
  def query(sql: String): QueryResponse

}

object DbRpcClient {

  /** RPC endpoint registered by godev `pkg/rpc/db/schema.go`. */
  val QueryEndpoint = "dbaccess.v1.Query"

  /**
   * Concrete client over the hermes mailbox RPC. Mirrors `a8.hermes.continuum.ServiceResolver` — wraps
   * `RpcClient.callTyped`, addressed to the `dbaccess` service mailbox. Auth rides the caller's mailbox
   * identity inside `rpcClient` (no DB credentials).
   *
   * `callTyped` returns `None` on transport/timeout/firewall error AND would for an absent response, so we
   * translate `None` into a thrown [[DbRpcException]] — a successful-but-empty result is a real
   * `QueryResponse` with `rowCount == 0`, which is distinct and never None.
   */
  def apply(rpcClient: RpcClient, dbServiceMailbox: Mailbox.MailboxAddress, defaultTimeout: scala.concurrent.duration.FiniteDuration = 30.seconds)(using Ctx): DbRpcClient =
    new DbRpcClient {
      override def query(sql: String): QueryResponse =
        rpcClient.callTyped[QueryRequest, QueryResponse](
          targetMailbox = dbServiceMailbox,
          endpoint = QueryEndpoint,
          request = QueryRequest(sql = sql),
          timeout = Some(defaultTimeout),
        ) match {
          case Some(resp) => resp
          case None       => throw new DbRpcException(s"dbaccess RPC failed (timeout/transport/firewall) for sql: $sql")
        }
    }

  class DbRpcException(message: String) extends RuntimeException(message)

}
