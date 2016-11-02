#!/usr/bin/env filebot -script


/*
 * Auto-extract all zip and rar archives.
 */
args.getFiles{ it.isArchive() }.each {
	def output = extract(file:it)

	output.each{ println "Extracted: " + it.path }
}
