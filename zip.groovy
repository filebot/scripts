#!/usr/bin/env -S filebot -script

/*
 * Usage:
 * filebot -script fn:zip -d /input --filter f.subtitle --output subtitles.zip
 */

// flush caches and logs to the application data folder
commit()

zip(file:args)
