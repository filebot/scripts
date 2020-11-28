#!/usr/bin/env filebot -script


println '\n# Environment Variables #'
_environment.toSorted{ it.key }.each{ k, v ->
	println "$k: $v"
}

println '\n# Java System Properties #'
_system.toSorted{ it.key }.each{ k, v ->
	println "$k: $v"
}

println '\n# Arguments #'
_args.argumentArray.eachWithIndex{ a, i ->
	println "args[$i] = $a"
}
