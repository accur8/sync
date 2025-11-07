package a8.shared.app

abstract class Constructor[A,B](using lifeCycleForB: HasLifecycle[B]) {

  def construct(input: A)(using ctx: Ctx): B = {
    val b = rawConstruct(input)
    lifeCycleForB.register(ctx, b)
    b
  }

  def rawConstruct(input: A): B

}
