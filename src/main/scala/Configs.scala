package bb.mixer


import com.typesafe.config.ConfigFactory

object Configs {

  val config = ConfigFactory.load("application.conf")

  val host = config.getString("app.server-conf.host")
  val port = config.getInt("app.server-conf.port")

  val mixerSleep = config.getString("app.mixer.base-sleep-time") //sleep interval between mix distributions.  This is the base sleep, it will be randomized.

  val pollInterval = config.getInt("app.poller.poll-interval-secs")

  val houseMixAddress = config.getString("app.house.house-mixer-address")
  val houseMainAddress = config.getString("app.house.house-main-address")
  val houseVigAddress = config.getString("app.house.house-vig-address") //where transaction fees get sent
  val houseGlobalInAddress = config.getString("app.house.house-main-address")


}
