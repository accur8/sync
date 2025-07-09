package a8.shared

import a8.shared.SharedImports.!!!
import a8.shared.app.Ctx
import ox.flow.Flow

import scala.reflect.ClassTag

object zreplace {


  object Equal {
    def default[A]: Equal[A] = !!!
    def make[A](f: (A, A) => Boolean): Equal[A] = !!!

    given Equal[String] = default
    given Equal[BigDecimal] = default
    given Equal[Int] = default

  }

  trait Equal[-A] {
    def ===(a: A, b: A): Boolean = equal(a, b)
    def equal(a: A, b: A): Boolean
  }

  trait Thunk[+A] { self =>
    type Self[+AA] <: Thunk[AA]
    def map[B](f: A => B): Self[B]
    def flatMap[B](f: A => Self[B]): Self[B]
//    def catchAll[B](f: Throwable => Self[B]): Self[B]
    def as[B](b: B): Self[B] = map(_ => b)
    def mapError(f: Throwable=>Throwable): Self[A]
  }

//  object Task
//
//  trait Task[+A] extends Thunk[A] {
//    type Thunk[+AA] = Task[AA]
////    def map[B](f: A => B): Task[B]
////    def flatMap[B](f: A => Task[B]): Task[B]
////    def catchAll[B](f: Throwable => Task[B]): Task[B]
//    def unwrap: A
////    def as[B](b: B): Task[B] = map(_ => b)
//  }

  trait Scope

  object CommandLineArgs {
    def apply(args: Array[String]): CommandLineArgs =
      new CommandLineArgs {
        val value: Seq[String] = args.toIndexedSeq
      }
  }

  trait CommandLineArgs {
    val value: Seq[String]
  }

  object Resource {

    val unit: Resource[Unit] = acquireRelease[Unit](())(_ => ())

    def acquireRelease[A](acquire: Ctx ?=> A)(release: A => Unit): Resource[A] =
      free.AcquireRelease(
        acquire = ctx => acquire(using ctx),
        release = release
      )

    object free {

      case class AcquireRelease[A](acquire: Ctx => A, release: A => Unit) extends Resource[A]
      case class Map[A, B](resource: Resource[A], f: A => B) extends Resource[B]
      case class FlatMap[A, B](resource: Resource[A], f: A => Resource[B]) extends Resource[B]
      case class CatchAll[A](resource: Resource[A], f: Throwable => Resource[A]) extends Resource[A]
      case class MapError[A](resource: Resource[A], f: Throwable => Throwable) extends Resource[A]

      def run[A](resource: Resource[A])(using ctx: Ctx): A = {
        resource match {
          case AcquireRelease(acquire, release) =>
            val a = acquire(ctx)
            ctx.register(
              new Ctx.Listener {
                override def onCompletion(ctx: Ctx, completion: Ctx.Completion): Unit =
                  release(a)
              }
            )
            a
          case Map(resource, f) =>
            f(run(resource))
          case FlatMap(resource, f) =>
            run(f(run(resource)))
          case CatchAll(resource, f) =>
            try {
              run(resource)
            } catch {
              case th: Throwable =>
                run(f(th))
            }
          case MapError(resource, f) =>
            try {
              run(resource)
            } catch {
              case th: Throwable =>
                throw f(th)
            }
        }
      }

    }

  }
  sealed trait Resource[+A] extends Thunk[A] {
    import Resource.free
    type Self[+AA] = Resource[AA]
    def map[B](f: A => B): Resource[B] = free.Map(this, f)
    def flatMap[B](f: A => Resource[B]): Resource[B] = free.FlatMap(this, f)
//    def catchAll(f: Throwable => Resource[A]): Resource[A] = free.CatchAll(this, f)
    def mapError(f: Throwable=>Throwable): Resource[A] = free.MapError(this, f)
    override def as[B](b: B): Resource[B] = map(_ => b)
    def unwrap(using Ctx): A = free.run(this)
//    def using[B](fn: A ?=> B): B
  }

  object XStream {

    object free {

      case class AcquireRelease[A,B](acquire: ()=>(A, Iterator[B]), release: A => Unit) extends XStream[B]
      case class Map[A, B](stream: XStream[A], f: A => B) extends XStream[B]

      def runFlow[A,B](stream: XStream[A])(f: Flow[A]=>B)(using Ctx): B =
        f(runBuildFlow(stream))

      private def runBuildFlow[A](stream: XStream[A])(using Ctx): Flow[A] = {
        stream match {
          case AcquireRelease(acquire, release) =>

            val (resource, iter) = acquire()
            lazy val doRelease = release(resource)
            val flow =
              Flow
                .fromIterator(iter)
                .onComplete(doRelease)

            summon[Ctx].register(
              new Ctx.Listener {
                override def onCompletion(ctx: Ctx, completion: Ctx.Completion): Unit =
                  doRelease
              }
            )

            flow

          case Map(stream, f) =>
            runBuildFlow(stream)
               .map(f)
        }
      }

    }

    def acquireRelease[A,B](acquire: =>(A,Iterator[B]))(release: A=>Unit): XStream[B] = {
      free.AcquireRelease(
        acquire = () => acquire,
        release = release,
      )
    }


//    def fromIterable[A](iter: Iterable[A]): XStream[A] =
//      !!!

//    def execute[A](task: Task[A]): XStream[A] =
//      !!!

  }

  trait XStream[A] {

    import XStream.free

    def map[B](fn: A => B): XStream[B] =
      free.Map(this, fn)

    def runForeach(fn: A => Unit)(using Ctx): Unit =
      free.runFlow(this)(_.runForeach(fn))

    def runCollect()(using Ctx): Iterable[A] =
      free.runFlow(this)(_.runToList())

    def runLast()(using Ctx): A =
      free.runFlow(this)(_.runLast())

  }

  /**
   * desperatley needs optimizations
   */
  object Chunk {

    def fromArray[A](array: Array[A]): Chunk[A] = {
      cats.data.Chain.fromSeq(array)
    }

    extension (chunk: Chunk[Byte])
      def toByteBuffer: java.nio.ByteBuffer =
        java.nio.ByteBuffer.wrap(chunk.toArray)

    extension [A: ClassTag](chunk: Chunk[A])
      def toArray: Array[A] =
        chunk.iterator.toArray

  }

  type Chunk[A] = cats.data.Chain[A]

//  trait Chunk[A] {
//    def size: Int
//    def iterator: Iterator[A]
//    def apply(i: Int): A
//    def drop(n: Int): Chunk[A]
//    def map[B](f: A => B): Chunk[B]
//    def mkString(sep: String): String
//    def toArray: Array[A]
//  }

}
