package a8.shared

import a8.shared.app.Ctx
import ox.flow.Flow

import scala.collection.immutable.ArraySeq
import scala.quoted.{Quotes, Type}
import scala.reflect.ClassTag

object zreplace {


  trait Scope

  object CommandLineArgs {
    def apply(args: Seq[String]): CommandLineArgs =
      new CommandLineArgs {
        val value: Seq[String] = args
      }
  }

  trait CommandLineArgs {
    val value: Seq[String]
  }

  object Resource {

    object AttributeKey {

      def apply[A: ClassTag] =
        Impl(summon[ClassTag[A]].toString)

      case class Impl(key: String) extends AttributeKey {
        override def withSuffix(suffix: String): AttributeKey =
          Impl(s"${key}.${suffix}")
      }

    }

    sealed trait AttributeKey {
      def key: String
      def withSuffix(suffix: String): AttributeKey
    }

    val unit: Resource[Unit] = acquireRelease[Unit](())(_ => ())

    def acquireRelease[A](acquire: Ctx ?=> A)(release: A => Unit): Resource[A] =
      free.AcquireRelease(
        acquire = ctx => acquire(using ctx),
        attributeKey = None,
        release = release
      )

    def acquireReleaseWithKey[A](attributeKey: AttributeKey)(acquire: Ctx ?=> A)(release: A => Unit): Resource[A] =
      free.AcquireRelease(
        acquire = ctx => acquire(using ctx),
        attributeKey = Some(attributeKey),
        release = release
      )

    object free {

      case class AcquireRelease[A](acquire: Ctx => A, attributeKey: Option[AttributeKey], release: A => Unit) extends Resource[A]
      case class Map[A, B](resource: Resource[A], f: A => B) extends Resource[B]
      case class FlatMap[A, B](resource: Resource[A], f: A => Resource[B]) extends Resource[B]
      case class CatchAll[A](resource: Resource[A], f: Throwable => Resource[A]) extends Resource[A]
      case class MapError[A](resource: Resource[A], f: Throwable => Throwable) extends Resource[A]

      def run[A](resource: Resource[A])(using ctx: Ctx): A = {
        resource match {
          case AcquireRelease(acquire, attributeKeyOpt, release) =>
            attributeKeyOpt
              .flatMap(ak => ctx.get[A](ak))
              .getOrElse {
                val a = acquire(ctx)
                attributeKeyOpt
                  .foreach(ctx.put(_, a))
                ctx.register(
                  new Ctx.Listener {
                    override def onCompletion(ctx: Ctx, completion: Ctx.Completion): Unit =
                      release(a)
                  }
                )
                a
              }
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
  sealed trait Resource[+A] {
    import Resource.free
    type Self[+AA] = Resource[AA]
    def map[B](f: A => B): Resource[B] = free.Map(this, f)
    def flatMap[B](f: A => Resource[B]): Resource[B] = free.FlatMap(this, f)
    def mapError(f: Throwable=>Throwable): Resource[A] = free.MapError(this, f)
    def as[B](b: B): Resource[B] = map(_ => b)
    def unwrap(using Ctx): A = free.run(this)
  }

  object XStream {

    object free {

      case class AcquireRelease[A,B](acquire: ()=>(A, Iterator[B]), release: A => Unit) extends XStream[B]
      case class Map[A, B](stream: XStream[A], f: A => B) extends XStream[B]
      case class OnComplete[A](stream: XStream[A], f: () => Unit) extends XStream[A]

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

          case OnComplete(stream, f) =>
            runBuildFlow(stream)
              .onComplete(f())

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

    def onComplete(fn: =>Unit): XStream[A] =
      free.OnComplete(this, () => fn)

  }

  /**
   * desperatley needs optimizations
   */
  object Chunk {

    def fromArray[A](array: Array[A]): Chunk[A] = {
      cats.data.Chain.fromSeq(ArraySeq.unsafeWrapArray(array))
    }

    extension (chunk: Chunk[Byte])
      def toByteBuffer: java.nio.ByteBuffer =
        java.nio.ByteBuffer.wrap(chunk.toArray)

    extension [A: ClassTag](chunk: Chunk[A])
      def toArray: Array[A] =
        chunk.iterator.toArray

  }

  type Chunk[A] = cats.data.Chain[A]


}
