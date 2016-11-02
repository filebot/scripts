#!/usr/bin/env filebot -script


import org.apache.pdfbox.pdmodel.*

args.getFiles{ f -> f.hasExtension('pdf') }.each { f->
	def document = PDDocument.load(f)
	def info = document.getDocumentInformation()

	// @see http://pdfbox.apache.org/cookbook/workingwithmetadata.html	
	log.finest "[$f.name] [$info.title] $info.dictionary"

	if (info.title) {
		def dest = new File(f.parentFile, info.title.validateFileName() + '.pdf')
		if (!f.equals(dest)) {
			if (f.renameTo(dest)) {
				println "[RENAME] [$f.name] to [$dest.name]"
			}
		}
	}
}
