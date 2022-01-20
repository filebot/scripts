#!/usr/bin/env filebot -script


osdbUser = any{ osdbUser }{ console.printf('Enter OpenSubtitles username: '); console.readLine() }
osdbPwd = any{ osdbPwd }{ console.printf('Enter OpenSubtitles password: '); console.readLine() }


/* --------------------------------------------------------------------- */


if (Settings.getApplicationRevisionNumber() < 9072) {
	// set login details (FileBot 4.9.4 r9071 or lower)
	if (osdbUser && osdbPwd) {
		log.config('Set OpenSubtitles login details')
		UserData.forPackage(WebServices.class).put(WebServices.LOGIN_OPENSUBTITLES, String.join(WebServices.LOGIN_SEPARATOR, osdbUser, osdbPwd))
		WebServices.OpenSubtitles.login(osdbUser, osdbPwd)
		printAccountInformation()
	}
	// clear login details (FileBot 4.9.4 r9071 or lower)
	if (!osdbUser && !osdbPwd) {
		log.config('Reset OpenSubtitles login details')
		UserData.forPackage(WebServices.class).remove(WebServices.LOGIN_OPENSUBTITLES)
	}
	return
}


// set login details (FileBot 4.9.5 r9072 or higher)
if (osdbUser && osdbPwd) {
	log.config('Set OpenSubtitles login details')
	WebServices.setLogin(WebServices.OpenSubtitles, osdbUser, osdbPwd)
	printAccountInformation()
}


// clear login details (FileBot 4.9.5 r9072 or higher)
if (!osdbUser && !osdbPwd) {
	log.config('Reset OpenSubtitles login details')
	WebServices.setLogin(WebServices.OpenSubtitles, null, null)
}




/**
 * Log in and retrieve account details.
 */
void printAccountInformation() {
	tryLogCatch{
		console.printf('Checking... ')
		def info = WebServices.OpenSubtitles.getServerInfo()
		console.printf('OK\n\n')

		info.download_limits.each{ n, v ->
			log.config("$n: $v")
		}
	}
}
