
object Test {

  import scala.testing.SUnit._
  import scala.collection.mutable.{ArrayBuffer, Buffer, BufferProxy, ListBuffer}

  trait BufferTest extends Assert {
    def doTest(x:Buffer[String]) = {
      // testing method += 
      x += "one"
      assert(x(0) == "one", "retrieving 'one'")
      assert(x.length == 1, "length A ")
      x += "two"
      assert(x(1) == "two", "retrieving 'two'")
      assert(x.length == 2, "length B ")

      // testing method -= (removing last element)
      x -=  "two"

      assert(x.length == 1, "length C ")

      try { x(1); fail("no exception for removed element") } 
      catch { case i:IndexOutOfBoundsException => }

      try { x.remove(1); fail("no exception for removed element") } 
      catch { case i:IndexOutOfBoundsException => }
      
      x += "two2"
      assert(x.length == 2, "length D ")

      // removing first element
      x.remove(0)
      assert(x.length == 1, "length E ")

      // toList
      assert(x.toList == List("two2"), "toList ")

      // clear
      x.clear
      assert(x.length == 0, "length F ")
      
      // copyToBuffer
      x += "a"
      x += "b"
      val dest = new ArrayBuffer[String]
      x copyToBuffer dest
      assert(List("a", "b") == dest.toList, "dest")
      assert(List("a", "b") == x.toList, "source")
    }
  }

  class TArrayBuffer extends TestCase("collection.mutable.ArrayBuffer") with Assert with BufferTest {

    var x: ArrayBuffer[String] = _

    override def runTest = { setUp; doTest(x); tearDown }
    
    override def setUp = { x = new scala.collection.mutable.ArrayBuffer }

    override def tearDown = { x.clear; x = null }
  }

  class TListBuffer extends TestCase("collection.mutable.ListBuffer") with Assert with BufferTest {

    var x: ListBuffer[String] = _

    override def runTest = { setUp; doTest(x); tearDown }

    override def setUp = { x = new scala.collection.mutable.ListBuffer }

    override def tearDown = { x.clear; x = null }

  }

  class TBufferProxy extends TestCase("collection.mutable.BufferProxy") with Assert with BufferTest {

    class BBuf(z:ListBuffer[String]) extends BufferProxy[String] {
      def self = z
    }

    var x: BufferProxy[String] = _

    override def runTest = { setUp; doTest(x); tearDown }

    override def setUp = { x = new BBuf(new scala.collection.mutable.ListBuffer) }

    override def tearDown = { x.clear; x = null }

  }

  def main(args:Array[String]) = {
    val ts = new TestSuite(
      //new TArrayBuffer, 
      new TListBuffer//, 
      //new TBufferProxy
    )
    val tr = new TestResult()
    ts.run(tr)
    for (failure <- tr.failures) {
      Console.println(failure)
    }
  }
}
