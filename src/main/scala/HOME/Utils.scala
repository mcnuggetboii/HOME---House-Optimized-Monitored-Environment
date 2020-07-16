package HOME

import scala.language.implicitConversions

object Constants {
  val testSleepTime :Int = 50 //millis, time the matcher waits to evaluate 'eventually' expression in tests
}

case class MyClass(_class: Any) {
  def getSimpleClassName: String = _class.getClass.getSimpleName.split("\\$").last

  def errUnexpected[A](item: Unexpected, value: String): A =
    throw new IllegalArgumentException("Unexpected " + item.item + ": " + value)
}

object MyClass{
  implicit def toMyClass(_class: Any): MyClass = MyClass(_class)
}

//helper object used by various devices to set the output strength
object ValueChecker {
  def apply(min: Int, max: Int)(value: Int): Int = value match {
    case x if x > max => max
    case x if x < min => min
    case _ => value
  }
}

sealed trait Unexpected {
  var item: String
}
case object UnexpectedTopic extends Unexpected {
  override var item: String = "topic"
}
case object UnexpectedMessage extends Unexpected {
  override var item: String = "message"
}
case object UnexpectedRoom extends Unexpected {
  override var item: String = "room"
}
case object UnexpectedDevice extends Unexpected {
  override var item: String = "device"
}
case object UnexpectedDeviceType extends Unexpected {
  override var item: String = "deviceType"
}