object Test extends App {
  val ya = <x>{{</x>
  assert(ya.text == "{")

  val ua = <x>}}</x>
  assert(ua.text == "}")

  val za = <x>{{}}{{}}{{}}</x>
  assert(za.text == "{}{}{}")
}
