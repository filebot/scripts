#!/usr/bin/env filebot -script


// watch folders and print files that were added/modified
def watchman = args[0].watch { changes ->
	// extract all
	if (_args.extract)
		changes += extract(file:changes.findAll{ it.isArchive() }, output:'.')

	// subtitles for all
	if (_args.getSubtitles)
		changes += getMissingSubtitles(file:changes.findAll{ it.isVideo() }, output:'srt')

	// rename all
	if (_args.rename)
		rename(file:changes)
}

watchman.commitDelay = 5 * 1000			// default = 5s
watchman.commitPerFolder = true			// default = true

println "Press ENTER to exit..."
console.readLine()
