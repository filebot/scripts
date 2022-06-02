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
	'Audio Profile': 'aco',
	'Audio Channels': 'channels',
	'Audio Languages': 'audioLanguages',
	'Subtitle Languages': 'textLanguages',
	'Duration': 'hours',
	'File Size': 'megabytes',
	'Path': 'f',
	'Original Name': 'original',
	'Extended Attributes': 'json'
]

def separator = '\t'
def header = model.keySet().join(separator)
def format = model.values().collect{ "{$it}" }.join(separator)

// open output file
def outputFile = any{ _args.output }{ 'MediaIndex.tsv' }.toFile().getCanonicalFile()

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
