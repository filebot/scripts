#!/usr/bin/env filebot -script


import static net.filebot.subtitle.SubtitleUtilities.*
import static net.filebot.web.OpenSubtitlesHasher.*


def lang = _args.language.locale
def strict = !_args.nonStrict

def fetch = any{ fetch as boolean }{ false }


args.getFiles{ it.isVideo() }.each{ f ->
	println "File: $f"

	// hash search
	def hash = computeHash(f)
	def size = f.length()
	def tag = ? f.metadata?.originalName?.nameWithoutExtension : f.nameWithoutExtension
	println "Hash/Tag Lookup (hash: $hash, size: $size, lang: $lang, tag: $tag)"

	def hashMatches = lookupSubtitlesByHash(WebServices.OpenSubtitles, [f], lang, true, strict).get(f)
	hashMatches.eachWithIndex{ d, i ->
		println "Result ${i+1}: ${d.properties}"
	}

	def bestHashMatch = getBestMatch(f, hashMatches, strict)
	println "Best Hash Match: ${bestHashMatch?.properties}"

	// name search
	def nameMatches = []
	if (!strict) {
		println "Name Lookup (file: $f.nameWithoutExtension, strict: $strict, lang: $lang)"
		nameMatches = findSubtitlesByName(WebServices.OpenSubtitles, [f], lang, null, true, strict).get(f)
		nameMatches.eachWithIndex{ d, i ->
			println "Result ${i+1}: ${d.properties}"
		}
		println "Best Name Match: ${nameMatches[0]?.properties}"
	}

	// fetch subtitles
	if (fetch) {
		[hashMatches, nameMatches].collectMany{ it ?: [] }.unique{ d -> d.path }.each{ d ->
			def s = f.resolveSibling(d.path)
			if (!s.exists()) {
				println "Fetch $s"
				d.fetch().saveAs(s)
			}
		}
	}
}
