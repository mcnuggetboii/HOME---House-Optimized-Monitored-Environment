package HOME

import org.scalatest.concurrent.Eventually
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import HOME.Constants._

class JSONUtilsTest extends AnyFunSuite with JSONUtils with Eventually with Matchers {

  val coordinator: Coordinator = CoordinatorImpl()
  val light: SimulatedLight = Light("A","salotto")

  test("The message + device/coordinator is encoded/decoded via JSON correctly") {
    val msgD: String = getMsg("testMsgD", light)
    val retrievedMessageD: String = getMessageFromMsg(msgD)
    val retrievedDevice: AssociableDevice = getSenderFromMsg(msgD).asInstanceOf[AssociableDevice]
    assert("testMsgD" == retrievedMessageD)
    assert(light.id == retrievedDevice.id)
    assert(light.room == retrievedDevice.room)
    assert(light.deviceType == retrievedDevice.deviceType)
    assert(light.consumption == retrievedDevice.consumption)
    assert(light.subTopic == retrievedDevice.subTopic)

    val msgC: String = getMsg("testMsgC", coordinator)
    val retrievedMessageC: String = getMessageFromMsg(msgC)
    val retrievedCoordinator: Coordinator = getSenderFromMsg(msgC).asInstanceOf[Coordinator]
    assert("testMsgC" == retrievedMessageC)
    assert(coordinator.name == retrievedCoordinator.name)
  }

  //This test needs the MQTT Broker active and running
  test( "Device registers correctly") {
    eventually { Thread.sleep(testSleepTime); light.connect should be (true) }
    eventually { Thread.sleep(testSleepTime); light.subscribe should be (true) }
    eventually { Thread.sleep(testSleepTime); coordinator.connect should be (true) }
    eventually { Thread.sleep(testSleepTime); coordinator.subscribe should be (true) }
    assert(!light.registered)
    eventually { Thread.sleep(testSleepTime); light.register should be (true) }
    eventually { Thread.sleep(testSleepTime); coordinator.devices.size should be (1) }
    eventually { Thread.sleep(testSleepTime); light.registered should be (true) }
    assert(light.register)
    eventually { Thread.sleep(testSleepTime); coordinator.devices.size should be (1) }
    assert(light.registered)
    val registeredDevice: Device = coordinator.devices.head
    assert(light.id == registeredDevice.id)
    assert(light.room == registeredDevice.room)
    assert(light.deviceType == registeredDevice.deviceType)
    assert(light.consumption == registeredDevice.consumption)
    assert(light.disconnect)
    eventually { Thread.sleep(testSleepTime); coordinator.devices.size should be (0)}
    assert(coordinator.disconnect)
  }
}