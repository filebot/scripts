#!/usr/bin/env filebot -script

log.finest "# ${MediaInfo.version()}"

args.files.each{ f ->
	log.fine "\n[$f]"
	MediaInfo.snapshot(f).each{ kind, streams ->
		// find optimal padding
		def pad = streams*.keySet().flatten().collect{ it.length() }.max()

		streams.each{ p -> 
			log.finest "\n[$kind]"
			p.each{ k,v -> 
				log.info "${k.padRight(pad)} : $v"
			}
		}
	}
}
