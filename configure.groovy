#!/usr/bin/env filebot -script


osdbUser = any{ osdbUser }{ console.printf('Enter OpenSubtitles username: '); console.readLine() }
osdbPwd = any{ osdbPwd }{ console.printf('Enter OpenSubtitles password: '); console.readLine() }


/* --------------------------------------------------------------------- */


if (Settings.getApplicationRevisionNumber() < 9072) {
	die "Sorry. OpenSubtitles Login now requires FileBot 4.9.5 (r9072) or higher due to server-side API changes."
}


// set login
if (osdbUser && osdbPwd) {
	console.printf('Testing OpenSubtitles login details... ')
	WebServices.setLogin(WebServices.OpenSubtitles, osdbUser, osdbPwd)
	console.printf('OK\n')
}


// clear login
if (osdbUser.empty && osdbPwd.empty) {
	console.printf('Clear OpenSubtitles login details... ')
	WebServices.setLogin(WebServices.OpenSubtitles, null, null)
	console.printf('OK\n')
}
