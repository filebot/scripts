#!/usr/bin/env filebot -script


osdbUser = any{ osdbUser }{ console.printf('Enter OpenSubtitles username: '); console.readLine() }
osdbPwd = any{ osdbPwd }{ console.printf('Enter OpenSubtitles password: '); console.readLine() }


/* --------------------------------------------------------------------- */


// user preferences are per-user and not per-device
if (java.util.prefs.Preferences.userRoot().class =~ /FileSystemPreferences/) {
	help "* Preferences for ${System.getProperty('user.name')} are stored at ${System.getProperty('user.home')}"
}


// set login details
if (osdbUser && osdbPwd) {
	log.config('Set OpenSubtitles login details')
	try {
		WebServices.setLogin(WebServices.OpenSubtitles, osdbUser, osdbPwd)
		printAccountInformation()
	} catch(e) {
		if (e.message == /401 Unauthorized/) {
			log.warning 'Your login details are incorrect. Please go to www.opensubtitles.org (and not www.opensubtitles.com) to check your login details and reset your password if necessary.'
		}
		die e.message
	}
}


// clear login details
if (!osdbUser && !osdbPwd) {
	log.config('Reset OpenSubtitles login details')
	WebServices.setLogin(WebServices.OpenSubtitles, null, null)
}




/**
 * Log in and retrieve account details.
 */
void printAccountInformation() {
	console.printf('Checking... ')
	def info = WebServices.OpenSubtitles.getServerInfo()
	console.printf('OK\n\n')

	info.download_limits.each{ n, v ->
		log.config("$n: $v")
	}
}
