package kontinuum

import kontinuum.ConfigProvider.config
import java.lang.System.exit


fun main(args: Array<String>) {

    if (!configFile.exists()) {
        println("config not found at $configFile")
        exit(1)
    }

    loadActiveIsues()
    println("got active issues: " + activeIssues.size)

    println("using config: " + config)
    startWebServer()
    watchChain()
}

