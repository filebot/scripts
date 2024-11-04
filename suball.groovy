#!/usr/bin/env -S filebot -script


def languages = any{ _args.lang.split(/\W+/) }{ ['en'] } as List

def minAgeDays = any{ minAgeDays.toDouble() }{  0d }
def maxAgeDays = any{ maxAgeDays.toDouble() }{ 30d }

def minFileSize = any{ minFileSize.toLong() }{ 50 * 1000 * 1000L }
def minLengthMS = any{ minLengthMS.toLong() }{ 10 *   60 * 1000L }

def ignore = any{ ignore }{ null }
def ignoreTextLanguage = any{ ignoreTextLanguage }{ languages.join('|') }

def minAgeTimeStamp = now.time - (minAgeDays.toDouble() * 24 * 60 * 60 * 1000L) as long
def maxAgeTimeStamp = now.time - (maxAgeDays.toDouble() * 24 * 60 * 60 * 1000L) as long


def accept = { f ->
	def creationDate = f.creationDate

	// ignore files that are too old
	if (maxAgeTimeStamp != null && creationDate < maxAgeTimeStamp) {
		log.finest "Ignore old: $f"
		return false
	}

	// ignore files that are too young
	if (minAgeTimeStamp != null && creationDate > minAgeTimeStamp) {
		log.finest "Ignore young: $f"
		return false
	}

	// ignore files that match the give ignore pattern
	if (f.path.findMatch(ignore)) {
		log.finest "Ignore pattern: $f"
		return false
	}

	// ignore files that are too small
	if (minFileSize > 0 && f.length() < minFileSize) {
		log.fine "Ignore small: $f [$f.displaySize]"
		return false
	}

	// ignore files that are too short
	if (minLengthMS > 0 && any{ f.mediaCharacteristics.duration.toMillis() < minLengthMS }{ false }) {
		log.fine "Ignore short: $f [$f.mediaCharacteristics.duration]"
		return false
	}

	// ignore files that already have subtitles
	if (ignoreTextLanguage != null && any{ f.mediaCharacteristics.subtitleLanguage.findMatch(ignoreTextLanguage) }{ false }) {
		log.fine "Ignore text language: $f [$f.mediaCharacteristics.subtitleLanguage]"
		return false
	}

	return true
}


/*
 * Get subtitles for all your media files  
 */
args.getFiles{ it.isVideo() && accept(it) }.groupBy{ it.dir }.each{ dir, files ->
	log.fine "Fetch subtitles for [$dir]"
	languages.each{
		getMissingSubtitles(lang: it, file: files, output: 'srt', encoding: 'UTF-8')
	}
}
