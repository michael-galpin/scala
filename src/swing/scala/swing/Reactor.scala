/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2007-2011, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */



package scala.swing

/**
 * The counterpart to publishers. Listens to events from registered publishers.
 */
trait Reactor {
  /**
   * All reactions of this reactor.
   */
  val reactions: Reactions = new Reactions.Impl
  /**
   * Listen to the given publisher as long as `deafTo` isn't called for
   * them.
   */
  def listenTo(ps: Publisher*) = for (p <- ps) p.subscribe(reactions)
  /**
   * Installed reaction won't receive events from the given publisher any longer.
   */
  def deafTo(ps: Publisher*) = for (p <- ps) p.unsubscribe(reactions)
}
