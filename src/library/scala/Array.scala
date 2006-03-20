/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2006, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id$


package scala;


import runtime._

object Array {

  /** Copy one array to another
   *  Equaivalent to System.arraycopy(src, srcPos, dest, destPos, length),
   *  except that this works also for plymorphic and boxed arrays
   */
  def copy(src: AnyRef, srcPos: Int, dest: AnyRef, destPos: Int, length: Int): Unit = src match {
    case xs: BoxedArray =>
      xs.copyTo(srcPos, dest, destPos, length)
    case _ =>
      dest match {
        case xs: BoxedArray =>
          xs.copyFrom(src, srcPos, destPos, length)
        case _ =>
          System.arraycopy(src, srcPos, dest, destPos, length)
      }
  }

  /** Concatenate all argument arrays into a single array
   */
  def concat[T](xs: Array[T]*) = {
    var len = 0
    for (val x <- xs) {
      len = len + x.length
    }
    val result = new Array[T](len)
    var start = 0
    for (val x <- xs) {
      copy(x, 0, result, start, x.length)
      start = start + x.length
    }
    result
  }

  /** Create a an array containing of successive integers.
   *
   *  @param from the value of the first element of the array
   *  @param end  the value of the last element fo the array plus 1
   *  @return the sorted array of all integers in range [from;end).
   */
  def range(start: Int, end: Int): Array[Int] = {
    val result = new Array[Int](end - start);
    for (val i <- Iterator.range(start, end)) result(i) = i
    result
  }
}

[cloneable,serializable]
final class Array[a](_length: Int) extends Seq[a] {
  def length: Int = throw new Error();
  def apply(i: Int): a = throw new Error();
  def update(i: Int, x: a): Unit = throw new Error();
  def elements: Iterator[a] = throw new Error();
  def subArray(from: Int, end: Int): Array[a] = throw new Error();
  def filter(p: a => Boolean): Array[a] = throw new Error();
  def map[b](f: a => b): Array[b] = throw new Error();
  def flatMap[b](f: a => Array[b]): Array[b] = throw new Error();
}
