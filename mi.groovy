#!/usr/bin/env filebot -script


/*
 * Print media info of all video files to TSV file  
 */
def model = [
	'Name': 'fn',
	'Container': 'cf',
	'Resolution': 'resolution',
	'Video Codec': 'vc',
	'Video Format': 'vf',
	'Audio Codec': 'ac',
	'Audio Channels': 'channels',
	'Audio Language(s)': 'audioLanguages',
	'Subtitle Language(s)': 'textLanguages',
	'Duration': 'hours',
	'File Size': 'bytes',
	'Path': 'f.canonicalPath',
	'Original Name': 'original',
	'Extended Attributes': 'json'
]

def separator = '\t'
def header = model.keySet().join(separator)
def format = model.values().collect{ "{$it}" }.join(separator)

// use last argument as output file
def outputFile = any{ _args.output }{ 'MediaIndex.tsv' }.toFile().getCanonicalFile()

// open destination file (writing files requires -trust-script)
outputFile.withWriter('UTF-8'){ output ->
	// print to console
	log.finest "Writing TSV file [$outputFile]"
	log.config header

	// print header
	output.println(header)

	// print info for each video file
	args.getFiles{ it.isVideo() }.each{
		def mi = getMediaInfo(it, format)

		// print to console
		log.info mi

		// append to file
		output.println(mi)
	}
}
