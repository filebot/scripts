// filebot -script fn:sysenv

println '# Arguments #'
_args.array.eachWithIndex{ a, i ->
	println "args[$i] = $a"
}

println '# Java System Properties #'
_system.each{
	println "$it.key: $it.value"
}

println '# Environment Variables #'
_environment.each{
	println "$it.key: $it.value"
}
