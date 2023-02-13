package a8.shared


import SharedImports._

object Synchronize {

  sealed trait Action[A]
  object Action {
    case class Insert[A](value: A) extends Action[A]
    case class Update[A](before: A, after: A) extends Action[A]
    case class Delete[A](value: A) extends Action[A]
    case class Noop[A](before: A, after: A) extends Action[A]
  }

  /**
   * Analyzes the source and target and returns
   * the Action's (Insert, Update, Delete, Noop) needed to be applied to the target for it to match the source
   */
  def apply[A](source: Vector[A], target: Vector[A]): Vector[Action[A]] =
    apply(source, target, identity)

  /**
   * Analyzes the source and target and returns
   * the Action's (Insert, Update, Delete, Noop) needed to be applied to the target for it to match the source
   */
  def apply[A,B](source: Vector[A], target: Vector[A], correlateBy: A=>B): Vector[Action[A]] = {

    import Action._

    val sourceBy = source.toMapTransform(correlateBy)
    val targetBy = target.toMapTransform(correlateBy)

    val deletesAndUpdatesAndNoops: Vector[Action[A]] =
      target
        .map(t => t -> sourceBy.get(correlateBy(t)))
        .map {
          case (s, Some(t)) =>
            // in both target and source
            if ( s == t )
              Noop(s, t)
            else
              Update(s, t)

          case (t, None) =>
            // in target not in source it is an insert
            Delete(t)

        }

    // in source and not in target is an insert
    val inserts: Vector[Action[A]] =
      source
        .filterNot(s => targetBy.contains(correlateBy(s)))
        .map(s => Insert(s))

    deletesAndUpdatesAndNoops ++ inserts

  }



  sealed trait AsymetricAction[A,B]
  object AsymetricAction {
    case class Insert[A,B](value: A) extends AsymetricAction[A,B]
    case class Update[A,B](before: A, after: B) extends AsymetricAction[A,B]
    case class Delete[A,B](value: B) extends AsymetricAction[A,B]
  }

  /**
   * Analyzes the source and target and returns
   * the Action's (Insert, Update, Delete, Noop) needed to be applied to the target for it to match the source
   */
  def asymetric[A,B,C](source: Iterable[A], target: Iterable[B], correlateSourceToC: A=>C, correlateTargetToC: B=>C): Vector[AsymetricAction[A,B]] = {

    import AsymetricAction._

    val sourceBy = source.toMapTransform(correlateSourceToC)
    val targetBy = target.toMapTransform(correlateTargetToC)

    val deletesAndUpdatesAndNoops: Iterable[AsymetricAction[A,B]] =
      target
        .map(t => t -> sourceBy.get(correlateTargetToC(t)))
        .map {
          case (t, Some(s)) =>
            Update[A,B](s, t)

          case (t, None) =>
            // in target not in source it is an insert
            Delete[A,B](t)

        }

    // in source and not in target is an insert
    val inserts: Iterable[Insert[A, B]] =
      source
        .filterNot(s => targetBy.contains(correlateSourceToC(s)))
        .map(s => Insert[A,B](s))

    (deletesAndUpdatesAndNoops ++ inserts).toVector

  }


}
