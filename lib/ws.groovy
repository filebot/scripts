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


/****************************************************************************
 * PushBullet
 * 				https://www.pushbullet.com
 ****************************************************************************/
def PushBullet(apikey, devices = null) {
	new PushBulletClient(apikey:apikey, devices:devices)
}

class PushBulletClient {
	def apikey
	def devices
	
	def url_devices = new URL('https://api.pushbullet.com/api/devices')
	def url_pushes = new URL('https://api.pushbullet.com/api/pushes')
	
	def sendHtml = { title, message ->
		def auth = "${apikey}".getBytes().encodeBase64().toString()
		def header = [requestProperties: [Authorization: "Basic ${auth}" as String]]
		
		def response = new groovy.json.JsonSlurper().parse(url_devices, header)
		def targets = response.devices.findAll{ devices == null || it.extras.nickname =~ devices }.findResults{ it.iden }
		
		targets.each{ device_iden ->
			def contentType = 'multipart/form-data; boundary=----------------------------a1134e1059ac'
			def multiPartFormData = """------------------------------a1134e1059ac
Content-Disposition: form-data; name="device_iden"

${device_iden}
------------------------------a1134e1059ac
Content-Disposition: form-data; name="type"

file
------------------------------a1134e1059ac
Content-Disposition: form-data; name="file"; filename="${title}"
Content-Type: text/html; charset=utf-8

${message}
------------------------------a1134e1059ac--
""" as String

			url_pushes.post(multiPartFormData, contentType, 'UTF-8', header.requestProperties)
		}
	}
}
