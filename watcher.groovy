#!/usr/bin/env filebot -script


// --output folder must be a valid folder
outputFolder = _args.absoluteOutputFolder

if (outputFolder == null || !outputFolder.isDirectory() || !outputFolder.canWrite()) {
	die "Invalid usage: output folder must exist and must be a writable directory: $outputFolder"
}


// watch folders and process files that were added / modified
def watchman = args[0].watchFolder{ changes ->
	// log input files
	changes.each{ f -> log.fine "Input: $f" }

	// extract archives to output directory
	if (_args.extract) {
		changes += extract(file: changes.findAll{ it.archive }, output: outputFolder / 'Archive')
	}

	// rename input files
	if (_args.rename){
		rename(file: changes)
	}
}

watchman.commitDelay = 5000			    // default = 5s
watchman.commitPerFolder = true			// default = true

println "Press ENTER to exit..."
console.readLine()
