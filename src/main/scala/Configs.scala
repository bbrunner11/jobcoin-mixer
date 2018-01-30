package bb.mixer


import com.typesafe.config.ConfigFactory

object Configs {

  val config = ConfigFactory.load("application.conf")

  val host = config.getString("app.server-conf.host")
  val port = config.getInt("app.server-conf.port")

  val mixerSleep = config.getString("app.mixer.base-sleep-time") //sleep interval between mix

  val houseMixAddress = config.getString("app.house.house-mixer-address")
  val houseMainAddress = config.getString("app.house.house-main-address")
  val houseGlobalInAddress = config.getString("app.house.house-main-address")


}
