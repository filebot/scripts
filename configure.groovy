#!/usr/bin/env filebot -script


osdbUser = any{ osdbUser }{ console.printf('Enter OpenSubtitles username: '); console.readLine() }
osdbPwd = any{ osdbPwd }{ console.printf('Enter OpenSubtitles password: '); console.readLine() }


/* --------------------------------------------------------------------- */


if (Settings.getApplicationRevisionNumber() < 9072) {
	die "Sorry. OpenSubtitles Login now requires FileBot 4.9.5 (r9072) or higher due to server-side API changes."
}


// set login details
if (osdbUser && osdbPwd) {
	console.printf('Testing OpenSubtitles login details... ')
	WebServices.setLogin(WebServices.OpenSubtitles, osdbUser, osdbPwd)
	// print account information
	def info = WebServices.OpenSubtitles.getServerInfo()
	console.printf('OK\n\n')

	info.download_limits.each{ n, v ->
		log.config("$n: $v")
	}
}


// clear login details
if (!osdbUser && !osdbPwd) {
	console.printf('Clear OpenSubtitles login details... ')
	WebServices.setLogin(WebServices.OpenSubtitles, null, null)
	console.printf('OK\n')
}
