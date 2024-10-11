#!/usr/bin/env -S filebot -script


osdbUser = any{ osdbUser }{ printf('Enter OpenSubtitles username: '); console.readLine() }
osdbPwd = any{ osdbPwd }{ printf('Enter OpenSubtitles password: '); console.readLine() }


/* --------------------------------------------------------------------- */


// user preferences are per-user and not per-device
log.fine "Store user preferences to [${UserData.root()}]"


// set login details
if (osdbUser && osdbPwd) {
	log.config('Set OpenSubtitles login details')
	try {
		WebServices.setLogin(WebServices.OpenSubtitles, osdbUser, osdbPwd)
		printAccountInformation()
	} catch(e) {
		if (e.message =~ /401 Unauthorized/) {
			if (WebServices.OpenSubtitles.class =~ /XmlRpc/) {
				log.warning 'Your login details are incorrect. Please go to www.opensubtitles.org (XML-RPC API; not www.opensubtitles.com) to check your login details and reset your password if necessary/'
			} else {
				log.warning 'Your login details are incorrect. Please go to www.opensubtitles.com (REST API; not www.opensubtitles.org) to check your login details and reset your password if necessary.'
			}
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
	printf('Checking... ')
	def info = WebServices.OpenSubtitles.getServerInfo()
	printf('OK\n\n')

	if (WebServices.OpenSubtitles.class =~ /XmlRpc/) {
		info.download_limits.each{ k, v ->
			log.config("$k: $v")
		}
	} else {
		info.each{ k, v ->
			log.config("$k: $v")
		}
	}

	WebServices.OpenSubtitles.logout()
}
