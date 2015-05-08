// filebot -script fn:configure

console.printf('Enter OpenSubtitles username: ')
def osdbUser = console.readLine()
console.printf('Enter OpenSubtitles password: ')
def osdbPwd = console.readLine()

/* --------------------------------------------------------------------- */

if (osdbUser) {
	console.printf('Testing OpenSubtitles... ')
	WebServices.setLogin(WebServices.LOGIN_OPENSUBTITLES, osdbUser, osdbPwd)
	WebServices.OpenSubtitles.login()
	console.printf('OK\n')
}
