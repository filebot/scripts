#!/usr/bin/env -S filebot -script


device = any{ _args.db }{ '*' }            // --db 'http://192.168.1.101:50001/desc/device.xml'
query  = any{ _args.query }{ '0' }         // --q 'ObjectID'
filter = any{ ~_args.filter }{ null }      // --filter 'Alias'
agent  = any{ agent }{ 'DLNADOC/1.50' }    // --def agent='User-Agent'
mx     = any{ mx }{ 2 }                    // --def mx='5'



def browse(controlURL, objectID, path = []) {
	def envelope = controlURL.post(
		"""<?xml version="1.0"?>
		<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
			<s:Body>
				<u:Browse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
					<ObjectID>${objectID}</ObjectID>
					<BrowseFlag>BrowseDirectChildren</BrowseFlag>
					<StartingIndex>0</StartingIndex>
					<RequestedCount>1000</RequestedCount>
				</u:Browse>
			</s:Body>
		</s:Envelope>
		""".replaceAll(/\n|\t/, '').getBytes('UTF-8'), 'text/xml; charset="utf-8"', ['User-Agent': agent, SOAPACTION: '"urn:schemas-upnp-org:service:ContentDirectory:1#Browse"']
	).text

	def response = new XmlSlurper().parseText(envelope).Body.BrowseResponse.Result.text()
	if (_args.outputPath) {
		def f = response.saveAs(_args.outputPath.resolve("${controlURL.host}_${controlURL.port}_ObjectID_${objectID}.xml".validateFileName()))
		help "* Save as ${f}"
	}

	def xml = new XmlSlurper().parseText(response)
	xml.item.each{ item ->
		if (filter == null || path.title =~ filter || item.title =~ filter) {
			log.fine "${'\t' * path.size()}[ObjectID=${item.'@id'}] ${item.title}"
			item.children().each{ element ->
				if (element.attributes()) {
					log.finest "${'\t' * path.size()}└─ ${element.name()} ${element.attributes().collect{ k, v -> k.removeBrackets() + '=' + v }} ${element.text()}"
				}
			}
		}
	}
	xml.container.each{ container ->
		if (filter == null || path.title =~ filter || container.title =~ filter) {
			log.info "${'\t' * path.size()}[ObjectID=${container.'@id'}] ${container.title}"
		}
		browse(controlURL, container.'@id', [*path, container])
	}
}



def crawl(deviceURL) {
	def response = deviceURL.get('User-Agent': agent).text
	if (_args.outputPath) {
		def f = response.saveAs(_args.outputPath.resolve("${deviceURL.host}_${deviceURL.port}_device.xml".validateFileName()))
		help "* Save as ${f}"
	}

	def xml = new XmlSlurper().parseText(response)
	log.info "[${xml.device.friendlyName}] ${deviceURL}"

	def controlURL = xml.device.serviceList.service.each{ service ->
		if (service.serviceType.text() == 'urn:schemas-upnp-org:service:ContentDirectory:1') {
			try {
				browse(deviceURL / service.controlURL.text(), query)	
			} catch(e) {
				log.severe "${e.message}"
			}
		}
	}
}



if (device == '*') {
	SSDP.discover('urn:schemas-upnp-org:device:MediaServer:1', mx){ ms ->
		crawl(ms.'LOCATION'.toURL())
	}
} else {
	crawl(device.toURL())
}
