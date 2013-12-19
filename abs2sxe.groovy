// filebot -script fn:abs2sxe <files>  [-non-strict]

// normalize absolute number format and add title and release date info that we can match by in the second step
def stage1 = rename(file:args.getFiles(), action:'rename', format:'{n} - {[e.pad(5)]} - {t} - {[d]}', order:'absolute', filter:'episode.season == null', db:'TheTVDB')

// process again, match by title/date and output SxE according to --format
rename(file:stage1, order:'airdate', db:'TheTVDB')
