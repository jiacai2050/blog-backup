
.PHONY: test
test:
	lein test

.PHONY: test-ci
test-ci:
	lein test-ci

.PHONY: deps
deps:
	lein deps
	npm install

.PHONY: release
release:
	lein do clean, release

.PHONY: publish
publish: release
	npm publish

.PHONY: dry-run
dry-run:
	npm publish --dry-run
