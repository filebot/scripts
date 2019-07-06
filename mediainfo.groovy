#!/usr/bin/env filebot -script

args.files.each{ f ->
	log.finer "\n[$f]"
	MediaInfo.snapshot(f).each{ kind, streams -> 
		// find optimal padding
		def pad = streams*.keySet().flatten().collect{ it.length() }.max()

		streams.each { properties -> 
			log.fine "\n[$kind]"
			properties.each{ k,v -> 
				println "${k.padRight(pad)} : $v"
			}
		}
	}
}
