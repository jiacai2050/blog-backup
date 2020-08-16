.PHONY: publish

publish:
	lein do clean, release && npm publish
