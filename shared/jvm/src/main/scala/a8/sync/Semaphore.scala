package a8.sync

import java.util.concurrent.{Semaphore => JSemaphore, TimeUnit}
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.concurrent.duration.*

/** A thin, Scala-friendly façade over `java.util.concurrent.Semaphore`. */
final class Semaphore private (private val j: JSemaphore):

  private val initialPermits = j.availablePermits()

  /** Current number of free permits. */
  def available(): Int           = j.availablePermits()

  /** Number of threads waiting to acquire a permit (fair semaphores only). */
  def queued(): Int              = j.getQueueLength

  /** Permits currently in use. */
  def inUse(): Int               = initialPermits - available()

  /** Acquire a single permit – **blocks** the calling thread. */
  def acquire(): Unit          = j.acquire()

  /** Try to acquire a permit immediately; returns `true` on success. */
  def tryAcquire(): Boolean    = j.tryAcquire()

  /** Try to acquire within the given timeout; returns `true` on success. */
  def tryAcquire(timeout: Duration): Boolean =
    j.tryAcquire(timeout.toNanos, TimeUnit.NANOSECONDS)

  /** Release one permit. */
  def release(): Unit          = j.release()

  /** Bracket pattern for synchronous code. */
  def withPermit[A](body: => A): A =
    acquire()
    try body
    finally release()

  /** Bracket pattern for asynchronous `Future` code. */
  def withPermitF[A](body: => Future[A])(using ec: ExecutionContext): Future[A] =
    val start = Future(blocking(acquire()))            // don’t starve the pool
    start.flatMap(_ => body).andThen { case _ => release() }

object Semaphore:
  /** Create a semaphore with `permits` slots.
   * `fair = true` gives FIFO ordering for waiting threads (slightly slower). */
  def apply(permits: Int, fair: Boolean = false): Semaphore =
    require(permits > 0, "permits must be positive")
    new Semaphore(new JSemaphore(permits, fair))
