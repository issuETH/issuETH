package kontinuum

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kontinuum.ConfigProvider.config
import kontinuum.model.config.Config
import okhttp3.OkHttpClient
import org.kethereum.extensions.clean0xPrefix
import org.ligi.kithub.GithubApplicationAPI
import org.ligi.kithub.model.GithubBaseEvent
import org.ligi.kithub.model.GithubIssueCloseEvent
import org.ligi.kithub.model.GithubIssueLabelEvent
import java.io.File
import java.io.FileFilter
import java.math.BigInteger

data class Token(val symbol: String, val address: String, val decimals: String)

val okhttp = OkHttpClient.Builder().build()!!

val configFile = File("config.json")
val dataPath = File("data")

val moshi = Moshi.Builder().build()!!

val configAdapter = moshi.adapter(Config::class.java)!!
val githubIssueLabelEventAdapter = moshi.adapter(GithubIssueLabelEvent::class.java)!!
val githubIssueCloseEventAdapter = moshi.adapter(GithubIssueCloseEvent::class.java)!!
val githubBaseEventAdapter = moshi.adapter(GithubBaseEvent::class.java)!!

var tokensListType = Types.newParameterizedType(List::class.java, Token::class.java)
var tokenListAdapter = moshi.adapter<List<Token>>(tokensListType)

var activeIssueListType = Types.newParameterizedType(List::class.java, ActiveIssue::class.java)
var blockHeightsType = Types.newParameterizedType(Map::class.java, String::class.java,String::class.java)
var processedTXListType = Types.newParameterizedType(List::class.java, String::class.java)
var issueHolderAdapter = moshi.adapter<List<ActiveIssue>>(activeIssueListType)
var processedTXAdapter = moshi.adapter<List<String>>(processedTXListType)

val githubInteractor by lazy { GithubApplicationAPI(config.github.integration, File(config.github.cert)) }
val activeIssues = mutableListOf<ActiveIssue>()
val processedTransactions = mutableListOf<String>()
val processedBlocks = mutableMapOf<String, BigInteger>()
val processedBlocksAdapter = moshi.adapter<Map<String, String>>(blockHeightsType)

data class ActiveIssue(val address: String, val project: String, val issue: String, val installation: String, val privteKey: String)

val activeIssueJSONFile = File("db.json")
val blockHeightsJSONFile = File("blocks.json")
val processedTXJSONFile = File("txdb.json")

fun saveBlockHeights() {
    val newMap = processedBlocks.toList().associate { it.first to it.second.toString() }
    blockHeightsJSONFile.bufferedWriter().use { it.write(processedBlocksAdapter.toJson(newMap)) }
}

fun loadBlockHeights() {
    if (blockHeightsJSONFile.exists()) {
        val oldElements = processedBlocksAdapter.fromJson(blockHeightsJSONFile.bufferedReader().use { it.readText() })
                ?.toList()?.associate { it.first to BigInteger(it.second) }
        if (oldElements != null) {
            processedBlocks.putAll(oldElements)
        }

    }
}

fun saveActiveIssues() {
    activeIssueJSONFile.bufferedWriter().use { it.write(issueHolderAdapter.toJson(activeIssues)) }
}

private fun saveProcessedTX() {
    processedTXJSONFile.bufferedWriter().use { it.write(processedTXAdapter.toJson(processedTransactions)) }
}

fun saveProcessedTX(txHash: String) {
    processedTransactions.add(txHash.toLowerCase())
    saveProcessedTX()
}

fun loadProcessedTransactions() {
    if (processedTXJSONFile.exists()) {
        val oldElements = processedTXAdapter.fromJson(processedTXJSONFile.bufferedReader().use { it.readText() })
        if (oldElements != null) {
            processedTransactions.addAll(oldElements)
        }
    }
}

fun loadActiveIsues() {
    if (activeIssueJSONFile.exists()) {
        val oldElements = issueHolderAdapter.fromJson(activeIssueJSONFile.bufferedReader().use { it.readText() })
        if (oldElements != null) {
            activeIssues.addAll(oldElements)
        }
    }
}

val tokenMap = mutableMapOf<String, Map<String, Token>>()
fun loadTokens() {
    File("data/tokens").listFiles(FileFilter { it.name.endsWith("json") }).forEach { tokenFile ->
        val cleanName = tokenFile.name.replace(".json", "")
        val possibleTokensForChain = tokenListAdapter.fromJson(tokenFile.reader().use { it.readText() })?.associate { it.address.toLowerCase().clean0xPrefix() to it }
        possibleTokensForChain?.let {
            tokenMap.put(cleanName.toLowerCase(), it)
        }


    }
    println(tokenMap)
}