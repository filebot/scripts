#!/usr/bin/env -S filebot -script


try {
	if (WebServices.OpenSubtitles.class =~ /XmlRpc/) {
		WebServices.OpenSubtitles.getServerInfo().download_limits.each{ k, v ->
			println "${k} = ${v}"
		}
	} else {
		WebServices.OpenSubtitles.getServerInfo().each{ k, v ->
			println "${k} = ${v}"
		}
	}
} catch(e) {
	log.severe e.message
}
