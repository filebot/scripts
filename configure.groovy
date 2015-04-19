// filebot -script fn:configure

console.printf('Enter OpenSubtitles username: ')
def osdbUser = console.readLine()
console.printf('Enter OpenSubtitles password: ')
def osdbPwd = console.readLine()


setLogin('osdb.user', osdbUser, osdbPwd)


/* --------------------------------------------------------------------- */

if (osdbUser) {
	console.printf('Testing OpenSubtitles... ')
	WebServices.OpenSubtitles.setUser(osdbUser, osdbPwd)
	WebServices.OpenSubtitles.login()
	console.printf('OK\n')
}

/* --------------------------------------------------------------------- */

def setLogin(key, user, pwd) {
	Settings.forPackage(WebServices.class).put(key, [user, pwd].join(':'))
}
