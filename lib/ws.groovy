/****************************************************************************
 * Pushover
 * 				https://pushover.net
 ****************************************************************************/
def Pushover(user, token = 'wcckDz3oygHSU2SdIptvnHxJ92SQKK') {
	new PushoverClient(user:user, token:token)
}

class PushoverClient {
	def user
	def token
	
	def endpoint = new URL('https://api.pushover.net/1/messages.xml')
	def titleLimit = 100
	def messageLimit = 512
	
	def send = { title, message ->
		def lines = message.readLines().collect{ it + '\n' }
		def pages = []
		
		def currentPage = ''
		lines.each{ line ->
			if (currentPage.length() + line.length() >= messageLimit) {
				pages << currentPage
				currentPage = ''
			}
			currentPage += line
		}
		pages << currentPage
		
		// use title and make space for pagination
		def pageTitle = (title as String)
		
		// submit post requests
		pages = pages.findAll{ it.length() > 0 }
		
		pages.eachWithIndex(){ m, i ->
			if (i > 0) sleep(1000) // max 1 request / sec
			def t = pages.size() <= 1 ? pageTitle : "${pageTitle} (${i+1}/${pages.size()})"
			post(m, [title: t])
		}
	}
	
	def post = { text, parameters = [:] ->
		// inject default post parameters
		parameters << [token:token, user:user, message:text as String]
		
		// post and process response
		endpoint.post(parameters)
	}
}
