package HOME

import java.util.concurrent.Executors

import HOME.CommandMsg.messageSeparator
import HOME.MyClass._
import org.eclipse.paho.client.mqttv3.{IMqttDeliveryToken, MqttCallback, MqttClient, MqttConnectOptions, MqttMessage}
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

/** Trait used for connection purposes **/
trait MQTTUtils extends JSONUtils {
  val retained: Boolean = true
  val topicSeparator: Char = '/'
  val logSeparator: Char = '_'
  val pubTopicPostFix: String = "From"
  val subTopicPostFix: String = "To"
  val broadcastTopic: String = "broadcast" //Topic the devices listen to for general messages
  val regTopic: String = "registration" //Topic used by the devices to register/disconnect to/from the system
  val updateTopic: String = "update"  //Topic used by the devices to confirm the update requested
  val sensorUpdateTopic: String = "sensorUpdate"  //Topic used by the sensors to send update messages
  val loggingTopic: String = "log"  //Topic used by the sensors to send log messages

  //Quality of Service
  //private val QoS_0: Int = 0
  private val QoS_1: Int = 1
  //private val QoS_2: Int = 2

  private var sender: JSONSender = _
  private var client: MqttClient = _

  private val mqttUserName: String = "HOME"
  private val mqttPwd: String = "7DGbTpxRFvHm9xk2"
  private val brokerURLDocker: String = "tcp://mosquitto-service:1883"
  private val brokerURLLocal: String = "tcp://localhost:1883"
  private val persistence: MemoryPersistence = new MemoryPersistence
  private val waitAfterPublish: Int = 50

  /** Exception class used internally **/
  private class ConnectionException(message: String) extends Exception(message)

  /** Connects the requestor to the broker
   *
   * @param _sender the requestor the wants to connect
   * @param onMessageReceived the method to call when the requestor receives a message
   * @param brokerURL the address of the broker to connect to
   * @return  [[Boolean]] whether the connection completed successfully
   */
  def connect(_sender: JSONSender, onMessageReceived: (String,String) => Unit, brokerURL: String = brokerURLDocker): Boolean = client match {
    case null => Try {
      sender = _sender
      client = new MqttClient(brokerURL, sender.name, persistence)
      val opts = new MqttConnectOptions
      opts.setCleanSession(true)
      opts.setWill(sender.lastWillTopic, getMsg(sender.lastWillMessage, sender).getBytes, QoS_1, !retained)
      opts.setUserName(mqttUserName)
      opts.setPassword(mqttPwd.toCharArray)

      val callback = new MqttCallback {
        override def deliveryComplete(token: IMqttDeliveryToken): Unit = {
          //Do nothing
        }

        override def connectionLost(cause: Throwable): Unit = {
          client.connect(opts) //Auto reconnects
        }

        /** The incoming message is handled by the onMessageReceived method on a dedicated Thread **/
        override def messageArrived(topic: String, message: MqttMessage): Unit = {
          implicit val context: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
          Future {onMessageReceived(topic, new String(message.getPayload))}
        }
      }
      client.setCallback(callback)
      client.connect(opts)
    } match {
      case Failure(exception) =>
        client = null
        //In case of failure, it tries to connect to the local broker
        if (brokerURL == brokerURLDocker) {
          connect(sender, onMessageReceived, brokerURLLocal)
        } else {
          throw new ConnectionException(s"ERROR : $exception + ${exception.getCause}")
        }
      case Success(_) => true
      case _ => throw new ConnectionException("Unexpected connection result")
    }
    case c if c.isConnected => true
    case _ => false
  }

  /** Disconnect from the broker after sending the last will message
   *
   * @return [[Boolean]] whether the disconnection completed successfully
   */
  def disconnect: Boolean = client match {
    case null => true
    case _ =>
      publish(sender.lastWillTopic, sender.lastWillMessage)
      client.disconnect()
      client = null
      true
  }

  /** Subscribes to the specified topic
   *
   * @param topic the topic to subscribe to
   * @return [[Boolean]] whether the subscription completed successfully
   */
  def subscribe(topic: String): Boolean = client match {
    case null => false
    case _ =>
      if (client.isConnected && topic != null && topic != "") client.subscribe(topic, QoS_1)
      true
  }

  /** Unsubscribes from the specified topic
   *
   * @param topic the topic to unsubscribe from
   * @return [[Boolean]] whether the unsubscription completed successfully
   */
  def unsubscribe(topic: String): Boolean = client match {
    case null => false
    case _ =>
      if (client.isConnected && topic != null && topic != "") client.unsubscribe(topic)
      true
  }

  /** Publishes a command message to the specified topic **/
  def publish(pubTopic:String, message: CommandMsg, retained: Boolean): Boolean =
    publish(pubTopic, message.toString, retained)

  /** Publishes a message to the specified topic
   *
   * @param pubTopic the topic to publish the message to
   * @param message the message to publish
   * @param retained whether the published message should be retained
   * @return [[Boolean]] whether the publish completed successfully
   */
  def publish(pubTopic:String, message: String, retained: Boolean = !retained): Boolean = client match {
    case null =>
      false
    case _ =>
      if (client.isConnected) client.getTopic(pubTopic).publish(getMsg(message, sender).getBytes, QoS_1, retained)
      Thread.sleep(waitAfterPublish)
      true
  }
}

/** Trait for a command message: each command message has an id, a command and a possible related value **/
trait CommandMsg {
  def id: Int
  def command: String
  def value: String

  override def toString: String = id.toString + messageSeparator + command + (if(value != null) messageSeparator + value else "")
}

object CommandMsg {
  case class CommandMsgImpl(override val id: Int, override val command: String, _value: Any = null) extends CommandMsg {
    override val value: String = if(_value != null) _value.toString else null
  }

  private val messageSeparator: Char = '_' //character used to separate data in specific device messages

  def fromString(msg: String): CommandMsg = msg.split(messageSeparator).length match {
    case 2 => CommandMsgImpl(msg.split(messageSeparator)(0).toInt, msg.split(messageSeparator)(1))
    case 3 => CommandMsgImpl(msg.split(messageSeparator)(0).toInt, msg.split(messageSeparator)(1), msg.split(messageSeparator)(2))
    case _ => this.errUnexpected(UnexpectedMessage, msg)
  }

  def apply(id: Int = Msg.nullCommandId, cmd: String, value: Any = null): CommandMsg = CommandMsgImpl(id, cmd, value)
}

/** Messages sent by Devices and Coordinator **/
object Msg {
  val nullCommandId: Int = 0

  val disconnect: String = "disconnect" //Message sent by the coordinator to tell a device to disconnect
  val disconnected: String = "disconnected" //Message sent when the connection is lost
  val register: String = "register" //Message sent by the device to register to the system
  val regSuccess: String = "regSuccess" //Message sent by the coordinator to assert the device has registered successfully

  //Commands
  val on: String = "on"
  val off: String = "off"
  val open: String = "open"
  val close: String = "close"
  val setIntensity: String = "setIntensity"
  val setTemperature: String = "setTemperature"
  val setHumidity: String = "setHumidity"
  val setVolume: String = "setVolume"
  val mute: String = "mute"
  val washingType: String = "washingType"
  val RPM: String = "RPM"
  val addExtra: String = "addExtra"
  val removeExtra: String = "removeExtra"
  val setProgram: String = "setProgram"
  val setMode: String = "setMode"

  //Sensor values
  val updateBaseString = "sensorUpdate"
  val temperatureRead: String = "temperatureRead"
  val humidityRead: String = "humidityRead"
  val intensityRead: String = "intensityRead"
  val motionDetected: String = "motionDetected"
}
