// filebot -script dev:suball /path/to/media -non-strict --def maxAgeDays=7


def languages = any{ _args.lang.split(/\W+/) }{ ['en'] } as List

def minAgeDays = any{ minAgeDays.toDouble() }{  0d }
def maxAgeDays = any{ maxAgeDays.toDouble() }{ 30d }
def maxAgeDaysLimit = any{ maxAgeDaysLimit.toBoolean() }{ true }

def minFileSize = any{ minFileSize.toLong() }{ 50 * 1000 * 1000L }
def minLengthMS = any{ minLengthMS.toLong() }{ 10 *   60 * 1000L }

def ignoreTextLanguage = any{ ignoreTextLanguage }{ languages.join('|') }

def minAgeTimeStamp = now.time - (minAgeDays.toDouble() * 24 * 60 * 60 * 1000L) as long
def maxAgeTimeStamp = now.time - (maxAgeDays.toDouble() * 24 * 60 * 60 * 1000L) as long


// sanity check
if (maxAgeDaysLimit && (maxAgeDays == null || maxAgeDays.toDouble() > 30)) {
	die "maxAgeDays must be between 0 and 30. $maxAgeDays not reasonable."
}


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
	if (ignore != null && f.path =~ ignore) {
		log.finest "Ignore pattern: $f"
		return false
	}

	// ignore files that are too small	
	if (minFileSize > 0 && f.length() < minFileSize) {
		log.fine "Ignore small: $f"
		return false
	}

	// ignore files that are too short
	if (minLengthMS > 0 && any{ getMediaInfo(f, '{duration}').toDouble() < minLengthMS }{ false }) {
		log.fine "Ignore short: $f"
		return false
	}

	// ignore files that already have subtitles
	if (ignoreTextLanguage != null && any{ getMediaInfo(f, '{textLanguages}').findMatch(ignoreTextLanguage) != null }{ false }) {
		log.fine "Ignore text language: $f"
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
