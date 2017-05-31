package kontinuum

import com.squareup.moshi.Moshi
import kontinuum.model.config.Config
import java.io.File

val configFile = File("config.json")

val moshi = Moshi.Builder().build()!!


val configAdapter = moshi.adapter(Config::class.java)!!

