#!/usr/bin/env -S filebot -script

// create an API key with write permissions at - https://www.myepisodes.com/account/apikeys/
// call script with " --def apikey=<yourPersonalAPIkey> tick=acquired|watched debug=false|true "

def mesapikey = tryQuietly { apikey } ?: login

if (!mesapikey) {
    println "[error] No API key provided! Please pass it via --def apikey=YOUR_TOKEN."
    return
}

def mesadd = tryQuietly { addshows.toBoolean() } ?: true

// Flexible reading of tick=watched or tick=acquired (default is 'acquired')
def mesupdate = tryQuietly { tick }?.toString()?.toLowerCase() ?: 'acquired'
if (mesupdate != 'watched' && mesupdate != 'acquired') {
    mesupdate = 'acquired'
}

def mesvalue = tryQuietly { value } ?: 'true'
def isDebug = tryQuietly { debug.toBoolean() } ?: false

// Logging helper functions
def logDebug = { msg -> if (isDebug) println "[debug] $msg" }
def logInfo = { msg -> println "[info] $msg" }
def logFailure = { msg -> println "[failure] $msg" }

def mes = new MyEpisodesApi(apikey: mesapikey)

logInfo("Loading personal show list from MyEpisodes (/v1/me/shows)...")
def myShows = mes.getPersonalShows()
logInfo("Found ${myShows.size()} shows in your personal list.")

// Normalization helper for name matching
def normalize = { s -> s == null ? '' : s.toLowerCase().replaceAll(/[^a-z0-9]/, '') }

args.getFiles{ it.isVideo() && parseEpisodeNumber(it) }.groupBy{ detectSeries(it).name }.each{ series, files ->
    if (series) {
        logDebug("Searching personal list for: '$series'")

        def matchedShow = myShows.find { normalize(it.name) == normalize(series) || normalize(it.showname) == normalize(series) }
        def targetShowId = matchedShow?.id ?: matchedShow?.showid

        if (!targetShowId) {
            logFailure("Series '$series' could not be found in your personal MyEpisodes list.")
            return
        }

        logInfo("Using show ID: $targetShowId for '$series'")

        files.each{ file ->
            def sxe = parseEpisodeNumber(file)
            if (sxe) {
                mes.update(targetShowId.toInteger(), sxe.season, sxe.episode, mesupdate, mesvalue.toBoolean())
                println "[$mesupdate] $series S${String.format('%02d', sxe.season)}E${String.format('%02d', sxe.episode)} [$file.name]"
            } else {
                println "[warn] Could not extract episode number from file: $file.name"
            }
        }
    }
}

/****************************************************************************
 * MyEpisodes API Wrapper
 ****************************************************************************/
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.time.Duration

class MyEpisodesApi {
    def apikey
    def host = "https://api.myepisodes.com"

    def client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    def sendRequest(method, endpoint, bodyData = null) {
        def url = "${host}${endpoint}"
        def cleanToken = apikey.trim()

        def builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer ${cleanToken}")
            .header("Accept", "application/json")
            .header("User-Agent", "Mozilla/5.0")

        if (bodyData != null) {
            builder.header("Content-Type", "application/json")
            String jsonBody = JsonOutput.toJson(bodyData)
            builder.method(method, HttpRequest.BodyPublishers.ofString(jsonBody))
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody())
        }

        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() >= 400) {
            throw new Exception("API Error (${response.statusCode()}) at $url: ${response.body()}")
        }

        return response.body() && response.body().trim() ? new JsonSlurper().parseText(response.body()) : null
    }

    def getPersonalShows() {
        def result = sendRequest("GET", "/v1/me/shows")
        if (result instanceof List) {
            return result
        } else if (result instanceof Map) {
            if (result.data instanceof List) return result.data
            if (result.shows instanceof List) return result.shows
        }
        return []
    }

    def update(showid, season, episode, statusType = 'acquired', value = true) {
        def endpoint = "/v1/me/episodes/${showid}/${season}/${episode}"
        def payload = [(statusType): value]
        sendRequest("PUT", endpoint, payload)
    }
}
