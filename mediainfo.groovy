#!/usr/bin/env filebot -script

args.files.each{ f ->
	log.fine "\n[$f]"
	MediaInfo.snapshot(f).each{ kind, streams -> 
		// find optimal padding
		def pad = streams*.keySet().flatten().collect{ it.length() }.max()

		streams.each { properties -> 
			log.finest "\n[$kind]"
			properties.each{ k,v -> 
				log.info "${k.padRight(pad)} : $v"
			}
		}
	}
}
