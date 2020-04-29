#!/usr/bin/env filebot -script


// use --format parameter to specify your own output format
def format = _args.format ?: '''${from}\t${to}'''
def template = new groovy.text.GStringTemplateEngine().createTemplate(format)


// use args to list history only for the given folders if desired
def accept(from, to) {
	return args.empty || (args.any{ to.absolutePath.startsWith(it.absolutePath) } && to.exists())
}


// read history file
def history = getPersistentRenameLog()

// sanity check
if (history.empty) {
	die "No History", ExitCode.NOOP
}

history.each{ from, to ->
	if (accept(from, to)) {
		println template.make(from:from, to:to)
	}
}
