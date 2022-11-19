#!/usr/bin/env filebot -script


// select all files or only files from the given folder
def accept(from, to) {
	return to.exists() && (args.empty || args.any{ a -> to.absolutePath.startsWith(a.absolutePath) })
}


// read history file and select current entries
def history = getPersistentRenameLog().findAll{ from, to -> accept(from, to) }


// sanity check
if (history.empty) {
	die "No History", ExitCode.NOOP
}


// select files from history and then pipe to -mediainfo --format or -find -exec as input
if (_args.format || _args.exec || _args.apply) {
	getMediaInfo(file: history.values())
	return
}


// print history as TSV file
history.each{ from, to ->
	println "${from}\t${to}"
}
