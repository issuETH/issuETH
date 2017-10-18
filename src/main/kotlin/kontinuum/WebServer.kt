package kontinuum

import org.jetbrains.ktor.application.call
import org.jetbrains.ktor.http.ContentType
import org.jetbrains.ktor.netty.embeddedNettyServer
import org.jetbrains.ktor.response.respondText
import org.jetbrains.ktor.routing.get
import org.jetbrains.ktor.routing.post
import org.jetbrains.ktor.routing.routing
import org.kethereum.crypto.Keys
import org.kethereum.crypto.Keys.PRIVATE_KEY_SIZE
import org.kethereum.extensions.toBytesPadded
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId
import org.walleth.khex.toHexString
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringBufferInputStream


fun startWebServer() {

    embeddedNettyServer(9002) {
        routing {
            post("/") {

                call.request.headers["X-GitHub-Event"]?.let {
                    println("processing github event: $it")
                    val payload = call.request.content[String::class]
                    processWebHook(it, payload)
                }
                call.respondText("Thanks GitHub!", ContentType.Text.Plain)
            }

            get("/") {
                val html = "hello issuETH"

                call.respondText(html, ContentType.Text.Html)
            }
        }
    }.start(wait = false)
}


fun processWebHook(event: String, payload: String) {
    val epochSeconds = LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond()

    when (event) {
        "issues" -> {
            val fromJson = githubBaseEventAdapter.fromJson(payload)
            when (fromJson!!.action) {
                "closed" -> {
                    val eventInfo = githubIssueCloseEventAdapter.fromJson(payload)!!
                    val activeIssue = activeIssues.firstOrNull { it.issue == eventInfo.issue.number.toString() && it.project == eventInfo.repository.full_name }

                    if (activeIssue == null) {
                        println("Issue was closed but had no bounty. No work for us.")
                    } else {
                        val assignee = eventInfo.issue.assignees.firstOrNull()

                        val body = if (assignee == null) {
                            "This issue was closed but had no assignee - to get the bounty-account you need to close the issue with one assignee!"
                        } else {
                            val userPGP = githubInteractor.getUserPGP(assignee.login!!, eventInfo.installation.id)
                            if (userPGP.isEmpty()) {
                                "No PGP info for the assignee - to get the bounty-account the assignee must set configure pgp in github."
                            } else {
                                val key = PGPUtils.readPublicKey(StringBufferInputStream(userPGP.first().public_key))

                                val byteArrayOutputStream = ByteArrayOutputStream()
                                PGPUtils.encryptFile(byteArrayOutputStream, activeIssue.privteKey, key, true, false)
                                "This is the private key encrypted for the assignee: <br/><br/>" + byteArrayOutputStream.toString().replace("\n", "<br/>")
                            }
                        }
                        githubInteractor.addIssueComment(
                                eventInfo.repository.full_name,
                                eventInfo.issue.number.toString(),
                                body
                                , eventInfo.installation.id)
                    }
                }
                "labeled" -> {
                    val eventInfo = githubIssueLabelEventAdapter.fromJson(payload)!!
                    if (eventInfo.label.name.toLowerCase() == "bounty") {

                        val dataPath = File(File(dataPath, eventInfo.repository.full_name), eventInfo.issue.number.toString())
                        if (!dataPath.exists()) {
                            dataPath.mkdirs()
                            val createEcKeyPair = Keys.createEcKeyPair()
                            val privateHEX = createEcKeyPair.privateKey.toBytesPadded(PRIVATE_KEY_SIZE).toHexString()

                            File(dataPath, "private").writer().use {
                                it.write(privateHEX)
                            }
                            val address = Keys.getAddress(createEcKeyPair)
                            File(dataPath, "public").writer().use {
                                it.write(address)
                            }
                            activeIssues.add(ActiveIssue(
                                    address,
                                    eventInfo.repository.full_name,
                                    eventInfo.issue.number.toString(),
                                    eventInfo.installation.id,
                                    privateHEX)
                            )

                            saveActiveIssues()

                            println("labeled " + eventInfo.label.name + " " + eventInfo.installation.id)

                            val qrContent = "ethereum:" + address
                            var body = "This issue now has a bounty-address via [issuETH](https://github.com/issuETH/issuETH).<br/>" +
                                    "![](https://chart.googleapis.com/chart?cht=qr&chs=256x256&chl=$qrContent)<br/>" +
                                    "Your bounty-address is [$address](ethereum:0x$address) <br/>"

                            ConfigProvider.config.chains.forEach {
                                body += "Watch [on " + it.name + "](" + it.address_base_url.replace("ADDRESSHASH",address)+")<br/>"
                            }
                            githubInteractor.addIssueComment(
                                    eventInfo.repository.full_name,
                                    eventInfo.issue.number.toString(),
                                    body
                                    , eventInfo.installation.id)
                        } else {
                            println("already exists")
                        }
                    }
                }
            }
        }
    }
}