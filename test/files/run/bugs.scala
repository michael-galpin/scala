//############################################################################
// Bugs
//############################################################################
// $Id$

//############################################################################
// Bug 135

object Bug135Test {

  import scala.collection.immutable.TreeMap;
  import scala.collection.immutable.Order;

  def main(args: Array[String]): Unit = {
    val intOrder =
	new Order((x:int,y:int) => x < y, (x:int,y:int) => x == y);
    val myMap:TreeMap[int,String] = new TreeMap(intOrder);
    val map1 = myMap + 42 -> "The answer";
    Console.println(map1.get(42));
  }

}

//############################################################################
// Bug 142

abstract class Bug142Foo1 { class Inner; def foo: Inner; foo; }
abstract class Bug142Foo2 { class Inner; def foo: Inner = {System.out.println("ok"); null};}
abstract class Bug142Foo3 { type  Inner; def foo: Inner; foo; }
abstract class Bug142Foo4 { type  Inner; def foo: Inner = {System.out.println("ok"); null.asInstanceOf[Inner]}; }

abstract class Bug142Bar1 { type  Inner; def foo: Inner = {System.out.println("ok"); null.asInstanceOf[Inner]}; }
abstract class Bug142Bar2 { type  Inner; def foo: Inner; foo; }
abstract class Bug142Bar3 { class Inner; def foo: Inner = {System.out.println("ok"); null}; }
abstract class Bug142Bar4 { class Inner; def foo: Inner; foo; }

object Bug142Test1 extends Bug142Foo1 with Bug142Bar1 {def main(args:Array[String]):Unit=();}
object Bug142Test2 extends Bug142Foo2 with Bug142Bar2 {def main(args:Array[String]):Unit=();}
object Bug142Test3 extends Bug142Foo3 with Bug142Bar3 {def main(args:Array[String]):Unit=();}
object Bug142Test4 extends Bug142Foo4 with Bug142Bar4 {def main(args:Array[String]):Unit=();}
object Bug142Test5 with    Bug142Foo1 with Bug142Bar1 {def main(args:Array[String]):Unit=();}
object Bug142Test6 with    Bug142Foo2 with Bug142Bar2 {def main(args:Array[String]):Unit=();}
object Bug142Test7 with    Bug142Foo3 with Bug142Bar3 {def main(args:Array[String]):Unit=();}
object Bug142Test8 with    Bug142Foo4 with Bug142Bar4 {def main(args:Array[String]):Unit=();}

object Bug142Test {
  def main(args:Array[String]): Unit = {
    Bug142Test1;
    Bug142Test2;
    Bug142Test3;
    Bug142Test4;
    Bug142Test5;
    Bug142Test6;
    Bug142Test7;
    Bug142Test8;
    ()
  }
}

//############################################################################
// Bug 166

object Bug166Test {
  import scala.collection.mutable.HashMap ;
  def main(args:Array[String]) = {
    val m:HashMap[String,String] = new HashMap[String,String];
    m.update("foo","bar");
  }
}

//############################################################################
// Bug 167

class Bug167Node(bar:Int) {
  val foo = {
    val bar = 1;
    bar
  }
}

object Bug167Test {
  def main(args: Array[String]): Unit = {
    if (new Bug167Node(0).foo != 1) System.out.println("bug 167");
  }
}

//############################################################################
// Bug 168

class Bug168Foo {
  class Bar;
  def foo = new Bar;
}

object Bug168Test {
  def main(args: Array[String]): Unit = {
    (new Bug168Foo).foo;
    ()
  }
}

//############################################################################
// Bug 174

class Bug174Foo[X] {

  class Tree;
  class Node extends Tree;


  val inner:Inner = new SubInner;

  trait Inner {
    def test: Bug174Foo[X]#Tree ;
  }

  class SubInner extends Inner {
    def test = new Node;
  }

}

object Bug174Test {
  def main(args: Array[String]): Unit = {
    (new Bug174Foo[Int]).inner.test;
    ()
  }
}

//############################################################################
// Main

object Test  {
  var errors: Int = 0;
  def test(bug: Int, def test: Unit): Unit = {
    System.out.println("<<< bug " + bug);
    try {
      test;
    } catch {
      case exception => {
        val name: String = Thread.currentThread().getName();
        System.out.print("Exception in thread \"" + name + "\" ");
        exception.printStackTrace();
        System.out.println();
        errors = errors + 1;
      }
    }
    System.out.println(">>> bug " + bug);
    System.out.println();
  }

  def main(args: Array[String]): Unit = {

    test(135, Bug135Test.main(args));
    test(142, Bug142Test.main(args));
    test(166, Bug166Test.main(args));
    test(167, Bug167Test.main(args));
    test(168, Bug168Test.main(args));
    test(174, Bug174Test.main(args));

    if (errors > 0) {
      System.out.println();
      System.out.println(errors + " error" + (if (errors > 1) "s" else ""));
    }
  }
}

//############################################################################
