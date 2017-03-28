#!/usr/bin/env filebot -script


/*
 * Extract all zip and rar archives into their current location
 */
args.getFiles{ it.isArchive() }.each {
	extract(file: it, output: it.dir)
}
