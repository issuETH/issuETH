package kontinuum

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kontinuum.ConfigProvider.config
import kontinuum.model.config.Config
import okhttp3.OkHttpClient
import org.ligi.kithub.GithubApplicationAPI
import org.ligi.kithub.model.GithubBaseEvent
import org.ligi.kithub.model.GithubIssueLabelEvent
import java.io.File



val okhttp = OkHttpClient.Builder().build()!!

val configFile = File("config.json")
val dataPath = File("data")

val moshi = Moshi.Builder().build()!!

val configAdapter = moshi.adapter(Config::class.java)!!
val githubIssueLabelEventAdapter = moshi.adapter(GithubIssueLabelEvent::class.java)!!
val githubBaseEventAdapter = moshi.adapter(GithubBaseEvent::class.java)!!

var listMyData = Types.newParameterizedType(List::class.java, ActiveIssue::class.java)
var issueHolderAdapter = moshi.adapter<List<ActiveIssue>>(listMyData)

val githubInteractor by lazy { GithubApplicationAPI(config.github.integration, File(config.github.cert)) }
val activeIssues = mutableListOf<ActiveIssue>()
data class ActiveIssue(val address: String, val project: String, val issue: String,val installation: String,val privteKey: String)

val activeIssueJSONFile = File("db.json")

fun saveActiveIssues() {
    activeIssueJSONFile.bufferedWriter().use { it.write(issueHolderAdapter.toJson(activeIssues)) }
}

fun loadActiveIsues() {
    if (activeIssueJSONFile.exists()) {
        val oldElements = issueHolderAdapter.fromJson(activeIssueJSONFile.bufferedReader().use { it.readText() })
        if (oldElements != null) {
            activeIssues.addAll(oldElements)
        }

        activeIssueJSONFile.bufferedWriter().use { it.write(issueHolderAdapter.toJson(activeIssues)) }
    }
}